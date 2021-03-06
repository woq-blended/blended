package blended.streams.jms

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.{FlowHeaderConfig, StreamFactories}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.processor.{AckProcessor, Collector}
import blended.util.logging.LogLevel

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
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
    processFlow: Flow[FlowEnvelope, FlowEnvelope, _],
    timeout: FiniteDuration,
    msgs: FlowEnvelope*
  )(implicit system: ActorSystem): Try[KillSwitch] =
    Try {

      val hasException: AtomicBoolean = new AtomicBoolean(false)
      val sendCount: AtomicInteger = new AtomicInteger(0)

      val (((actor: ActorRef, killswitch: KillSwitch), done: Future[Done]), errEnv: Future[Option[FlowEnvelope]]) =
        StreamFactories
          .actorSource[FlowEnvelope](msgs.size)
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
      val start: Long = System.currentTimeMillis()

      // We will wait until all messages have passed through the process flow and check if
      // any have thrown an exception causing the stream to fail
      do {
        Thread.sleep(10)

        if (hasException.get()) {
          Await.result(errEnv, 1.second).flatMap(_.exception).foreach(t => throw t)
        }

        // if the stream has is finished before sending off all the messages, something went wrong.
        if (done.isCompleted) {
          throw new Exception("Failed to send messages to stream")
        }
      } while (!hasException.get && sendCount.get < msgs.size && (System
        .currentTimeMillis() - start) < timeout.toMillis)

      if (sendCount.get < msgs.size) {
        killswitch.shutdown()
        throw new Exception(s"failed to send messages to stream after [$timeout]")
      }

      killswitch
    }

  def sendMessages(
    producerSettings: JmsProducerSettings,
    log: FlowEnvelopeLogger,
    timeout: FiniteDuration,
    msgs: FlowEnvelope*
  )(implicit system: ActorSystem): Try[KillSwitch] = {

    val producer: Flow[FlowEnvelope, FlowEnvelope, _] = jmsProducer(
      name = producerSettings.jmsDestination.map(_.asString).getOrElse("producer"),
      settings = producerSettings,
      autoAck = true
    )

    processMessages(
      processFlow = producer,
      timeout = timeout,
      msgs = msgs: _*
    )
  }

  def receiveMessages(
    headerCfg: FlowHeaderConfig,
    cf: IdAwareConnectionFactory,
    dest: JmsDestination,
    log: FlowEnvelopeLogger,
    listener: Integer = 1,
    minMessageDelay: Option[FiniteDuration] = None,
    selector: Option[String] = None,
    completeOn: Option[Seq[FlowEnvelope] => Boolean] = None,
    timeout: Option[FiniteDuration],
    ackTimeout: FiniteDuration
  )(implicit system: ActorSystem): Collector[FlowEnvelope] = {

    val listenerCount: Int = if (dest.isInstanceOf[JmsQueue]) {
      listener
    } else {
      1
    }

    val collected: FlowEnvelope => Boolean = { env =>
      env.acknowledge()
      log.logEnv(env, LogLevel.Debug, s"Acknowledged envelope [${env.id}]")
      true
    }

    val source: Source[FlowEnvelope, NotUsed] = jmsConsumer(
      name = dest.asString,
      settings = JmsConsumerSettings(log = log, headerCfg = headerCfg, connectionFactory = cf, ackTimeout = ackTimeout)
        .withAcknowledgeMode(AcknowledgeMode.ClientAcknowledge)
        .withSessionCount(listenerCount)
        .withDestination(Some(dest))
        .withSelector(selector),
      minMessageDelay = minMessageDelay
    )

    StreamFactories.runSourceWithTimeLimit(
      name = dest.asString,
      source = source,
      timeout = timeout,
      onCollected = Some(collected),
      completeOn = completeOn
    )
  }

  def jmsProducer(
    name: String,
    settings: JmsProducerSettings,
    autoAck: Boolean
  )(implicit system: ActorSystem): Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val f = Flow.fromGraph(new JmsProducerStage(name, settings)).named(name)

    if (autoAck) {
      f.via(new AckProcessor(s"ack-$name").flow.named(s"ack-$name"))
    } else {
      f
    }
  }

  def jmsConsumer(
    name: String,
    settings: JmsConsumerSettings,
    minMessageDelay: Option[FiniteDuration]
  )(implicit system: ActorSystem): Source[FlowEnvelope, NotUsed] =
    Source.fromGraph(new JmsConsumerStage(name, settings, minMessageDelay))
}
