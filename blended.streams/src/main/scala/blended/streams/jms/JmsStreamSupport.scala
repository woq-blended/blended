package blended.streams.jms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.{KillSwitch, Materializer}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.StreamFactories
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{AckProcessor, Collector}
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait JmsStreamSupport {
  /**
    * Send messages to a given Jms destination using an actor source.
    * The resulting stream will expose a killswitch, so that it stays
    * open and the test code needs to tear it down eventually.
    */
  def sendMessages(
    headerCfg : FlowHeaderConfig,
    cf: IdAwareConnectionFactory,
    dest: JmsDestination,
    log: Logger,
    msgs : FlowEnvelope*
  )(implicit system: ActorSystem, materializer: Materializer, ectxt: ExecutionContext): KillSwitch = {

    // Create the Jms producer to send the messages
    val settings: JmsProducerSettings = JmsProducerSettings(
      headerConfig = headerCfg,
      connectionFactory = cf,
      connectionTimeout = 1.second,
      jmsDestination = Some(dest)
    )

    val toJms = jmsProducer(
      name = dest.asString,
      settings = settings,
      autoAck = true,
      log = log
    )

    // Materialize the stream, send the test messages and expose the killswitch
    StreamFactories.keepAliveFlow(toJms, msgs:_*)
  }

  def receiveMessages(
    headerCfg : FlowHeaderConfig,
    cf : IdAwareConnectionFactory,
    dest : JmsDestination
  )(implicit timeout : FiniteDuration, system: ActorSystem, materializer: Materializer) : Collector[FlowEnvelope] = {

    StreamFactories.runSourceWithTimeLimit(
      dest.asString,
      RestartableJmsSource(
        name = dest.asString,
        settings = JMSConsumerSettings(connectionFactory = cf, headerConfig = headerCfg).withSessionCount(2).withDestination(Some(dest))
      ),
      timeout
    )
  }

  def jmsProducer(
    name: String,
    settings: JmsProducerSettings,
    autoAck: Boolean = false,
    log: Logger
  )(implicit system: ActorSystem, materializer: Materializer): Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val f = Flow.fromGraph(new JmsSinkStage(name, settings, log))

    if (autoAck) {
      f.via(new AckProcessor(s"ack-$name").flow(log))
    } else {
      f
    }
  }
}
