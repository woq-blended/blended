package blended.streams.jms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, RestartSource, Source}
import akka.stream.{ActorMaterializer, KillSwitch, Materializer}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsDurableTopic, JmsQueue}
import blended.streams.{StreamController, StreamControllerConfig, StreamFactories}
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
    cf: IdAwareConnectionFactory,
    dest: JmsDestination,
    log: Logger,
    msgs : FlowEnvelope*
  )(implicit system: ActorSystem, materializer: Materializer, ectxt: ExecutionContext): KillSwitch = {

    // Create the Jms producer to send the messages
    val settings: JmsProducerSettings = JmsProducerSettings(
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
    dest : JmsDestination,
    log : Logger
  )(implicit timeout : FiniteDuration, system: ActorSystem, materializer: Materializer) : Collector[FlowEnvelope] = {

    val listener : Int = if (dest.isInstanceOf[JmsQueue]) {
      2
    } else {
      1
    }

    StreamFactories.runSourceWithTimeLimit(
      dest.asString,
      restartableConsumer(
        name = dest.asString,
        headerConfig = headerCfg,
        log = log,
        settings =
          JMSConsumerSettings(connectionFactory = cf)
            .withSessionCount(listener)
            .withDestination(Some(dest))
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

    val f = Flow.fromGraph(new JmsSinkStage(name, settings, log)).named(name)

    if (autoAck) {
      f.via(new AckProcessor(s"ack-$name").flow(log).named(s"ack-$name"))
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
