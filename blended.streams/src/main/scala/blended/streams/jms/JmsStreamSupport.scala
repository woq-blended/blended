package blended.streams.jms

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, RestartSource, Sink, Source}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.StreamFactories
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{AckProcessor, Collector}
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

trait JmsStreamSupport {
  /**
    * Process a sequence of messages with a given flow. If any of the processed
    * messages cause an exception in the provided flow, the Stream will terminate
    * with this exeption been thrown.
    *
    * The resulting stream will expose a killswitch, so that it stays
    * open and the test code needs to tear it down eventually.
    */

  def processMessages(
    processFlow : Flow[FlowEnvelope, FlowEnvelope, _],
    msgs : FlowEnvelope*
  )(implicit system: ActorSystem) : Try[KillSwitch]  = Try {

    implicit val materializer : Materializer = ActorMaterializer()
    implicit val eCtxt : ExecutionContext = system.dispatcher

    val hasException : AtomicBoolean = new AtomicBoolean(false)
    val sendCount : AtomicInteger = new AtomicInteger(0)

    val ((actor : ActorRef, killswitch : KillSwitch), errEnv: Future[Option[FlowEnvelope]]) =
      Source.actorRef[FlowEnvelope](msgs.size, OverflowStrategy.fail)
        .viaMat(processFlow)(Keep.left)
        .viaMat(KillSwitches.single)(Keep.both)
        .via(Flow.fromFunction[FlowEnvelope, FlowEnvelope]{env =>
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
    } while(!hasException.get && sendCount.get < msgs.size)

    killswitch
  }

  def sendMessages(
    producerSettings: JmsProducerSettings,
    log : Logger,
    msgs : FlowEnvelope*
  )(implicit system: ActorSystem, materializer: Materializer, ectxt: ExecutionContext): Try[KillSwitch] = {

    val producer : Flow[FlowEnvelope, FlowEnvelope, _] = jmsProducer(
      name = producerSettings.jmsDestination.map(_.asString).getOrElse("producer"),
      settings = producerSettings,
      autoAck = true,
      log = log
    )

    processMessages(
      processFlow = producer,
      msgs = msgs:_*
    )
  }

  def receiveMessages(
    headerCfg : FlowHeaderConfig,
    cf : IdAwareConnectionFactory,
    dest : JmsDestination,
    log : Logger
  )(implicit timeout : FiniteDuration, system: ActorSystem, materializer: Materializer) : Collector[FlowEnvelope] = {

    val listener : Int = if (dest.isInstanceOf[JmsQueue]) {
      2
    } else {
      1
    }

    val collected : FlowEnvelope => Unit = env => env.acknowledge()

    StreamFactories.runSourceWithTimeLimit(
      dest.asString,
      jmsConsumer(
        name = dest.asString,
        headerConfig = headerCfg,
        log = log,
        settings =
          JMSConsumerSettings(connectionFactory = cf)
            .withAcknowledgeMode(AcknowledgeMode.ClientAcknowledge)
            .withSessionCount(listener)
            .withDestination(Some(dest))
      ),
      timeout
    )(collected)
  }

  def jmsProducer(
    name: String,
    settings: JmsProducerSettings,
    autoAck: Boolean = false,
    log: Logger
  )(implicit system: ActorSystem, materializer: Materializer): Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val f = Flow.fromGraph(new JmsSinkStage(name, settings, log)).named(name)

    if (autoAck) {
      f.via(new AckProcessor(s"ack-$name").flow.named(s"ack-$name"))
    } else {
      f
    }
  }

  def jmsConsumer(
    name : String,
    settings : JMSConsumerSettings,
    headerConfig: FlowHeaderConfig,
    log: Logger
  )(implicit system: ActorSystem): Source[FlowEnvelope, NotUsed] = {

    if (settings.acknowledgeMode == AcknowledgeMode.ClientAcknowledge) {
      Source.fromGraph(new JmsAckSourceStage(name, settings, headerConfig, log))
    } else {
      Source.fromGraph(new JmsSourceStage(name, settings, headerConfig, log))
    }
  }

  def restartableConsumer(
    name : String,
    settings : JMSConsumerSettings,
    headerConfig: FlowHeaderConfig,
    log: Logger
  )(implicit system: ActorSystem) : Source[FlowEnvelope, NotUsed] = {

    implicit val materializer : Materializer = ActorMaterializer()

    val innerSource : Source[FlowEnvelope, NotUsed] = jmsConsumer(name, settings, headerConfig, log)

    RestartSource.withBackoff(
      minBackoff = 2.seconds,
      maxBackoff = 2.seconds,
      randomFactor = 0.2,
      maxRestarts = 10,
    ) { () => innerSource }
  }
}
