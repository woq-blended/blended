package blended.streams.jms

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.StreamFactories
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{AckProcessor, Collector}
import blended.streams.FlowHeaderConfig
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

trait JmsStreamSupport {
  /**
   * Process a sequence of messages with a given flow. If any of the processed
   * messages cause an exception in the provided flow, the Stream will terminated
   * with this exception been thrown.
   *
   * The resulting stream will expose a killswitch, so that it stays
   * open and the test code needs to tear it down eventually.
   */

  def processMessages(
    processFlow : Flow[FlowEnvelope, FlowEnvelope, _],
    msgs : FlowEnvelope*
  )(implicit system : ActorSystem) : Try[KillSwitch] = Try {

    implicit val materializer : Materializer = ActorMaterializer()
    implicit val eCtxt : ExecutionContext = system.dispatcher

    val hasException : AtomicBoolean = new AtomicBoolean(false)
    val sendCount : AtomicInteger = new AtomicInteger(0)

    val (((actor : ActorRef, killswitch : KillSwitch), done : Future[Done]), errEnv : Future[Option[FlowEnvelope]]) =
      Source.actorRef[FlowEnvelope](msgs.size, OverflowStrategy.fail)
        .viaMat(processFlow)(Keep.left)
        .viaMat(KillSwitches.single)(Keep.both)
        .watchTermination()(Keep.both)
        .via(Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
          if (env.exception.isDefined) {
            env.exception.foreach { t =>
              hasException.set(true)
              throw t
            }
          } else {
            sendCount.incrementAndGet()
          }
          env
        })
        .filter(_.exception.isDefined)
        .toMat(Sink.headOption[FlowEnvelope])(Keep.both)
        .run()

    // Send all the messages
    msgs.foreach(m => actor ! m)

    // We will wait until all messages have passed through the process flow and check if
    // any have thrown an exception causing the stream to fail
    do {
      Thread.sleep(10)

      if (hasException.get()) {
        Await.result(errEnv, 1.second).flatMap(_.exception).foreach(t => throw t)
      }

      // if the stream has is finished before sending off all the messages, something went wrong.
      // TODO: This looks strange
      if (done.isCompleted) {
        throw new Exception("Failed to create flow.")
      }

    } while (!hasException.get && sendCount.get < msgs.size)

    killswitch
  }

  def sendMessages(
    producerSettings : JmsProducerSettings,
    log : Logger,
    msgs : FlowEnvelope*
  )(implicit system : ActorSystem, materializer : Materializer, ectxt : ExecutionContext) : Try[KillSwitch] = {

    val producer : Flow[FlowEnvelope, FlowEnvelope, _] = jmsProducer(
      name = producerSettings.jmsDestination.map(_.asString).getOrElse("producer"),
      settings = producerSettings,
      autoAck = true
    )

    processMessages(
      processFlow = producer,
      msgs = msgs : _*
    )
  }

  def receiveMessages(
    headerCfg : FlowHeaderConfig,
    cf : IdAwareConnectionFactory,
    dest : JmsDestination,
    log : Logger,
    listener : Integer = 2,
    minMessageDelay : Option[FiniteDuration] = None,
    selector : Option[String] = None,
    completeOn : Option[Seq[FlowEnvelope] => Boolean] = None
  )(implicit timeout : FiniteDuration, system : ActorSystem, materializer : Materializer) : Collector[FlowEnvelope] = {

    val listenerCount : Int = if (dest.isInstanceOf[JmsQueue]) {
      listener
    } else {
      1
    }

    val collected : FlowEnvelope => Unit = { env =>
      log.debug(s"Acknowledging envelope [${env.id}]")
      env.acknowledge()
    }

    val source : Source[FlowEnvelope, NotUsed] = jmsConsumer(
      name = dest.asString,
      settings =
        JMSConsumerSettings(log = log, headerCfg = headerCfg, connectionFactory = cf)
          .withAcknowledgeMode(AcknowledgeMode.ClientAcknowledge)
          .withSessionCount(listenerCount)
          .withDestination(Some(dest))
          .withSelector(selector),
      minMessageDelay = minMessageDelay
    )

    StreamFactories.runSourceWithTimeLimit(dest.asString, source, timeout, completeOn)(collected)
  }

  def jmsProducer(
    name : String,
    settings : JmsProducerSettings,
    autoAck : Boolean
  )(implicit materializer : Materializer) : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val f = Flow.fromGraph(new JmsProducerStage(name, settings)).named(name)

    if (autoAck) {
      f.via(new AckProcessor(s"ack-$name").flow.named(s"ack-$name"))
    } else {
      f
    }
  }

  def jmsConsumer(
    name : String,
    settings : JMSConsumerSettings,
    minMessageDelay : Option[FiniteDuration]
  )(implicit system : ActorSystem) : Source[FlowEnvelope, NotUsed] =
    Source.fromGraph(new JmsConsumerStage(name, settings, minMessageDelay))
}
