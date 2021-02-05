package blended.streams.jms.internal

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.OverflowStrategy
import blended.container.context.api.ContainerContext
import blended.jms.utils.{BlendedSingleConnectionFactory, JmsDestination, ProducerMaterialized}
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig, StreamController, StreamFactories}
import blended.util.logging.{LogLevel, Logger}

import scala.concurrent.duration._

class StreamKeepAliveProducerFactory(
  override val ctCtxt : ContainerContext,
  override val cf : BlendedSingleConnectionFactory,
  streamsCfg : BlendedStreamsConfig
)(implicit system: ActorSystem) extends KeepAliveProducerFactory with JmsStreamSupport {

  private var stream : Option[ActorRef] = None

  private val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(
    FlowHeaderConfig.create(ctCtxt), Logger(s"blended.streams.keepalive.${cf.vendor}.${cf.provider}")
  )

  private val producerSettings : BlendedSingleConnectionFactory => JmsProducerSettings = bcf => JmsProducerSettings(
    log = envLogger,
    headerCfg = FlowHeaderConfig.create(ctCtxt),
    connectionFactory = bcf,
    jmsDestination = Some(JmsDestination.create(bcf.config.keepAliveDestination).get),
    timeToLive = Some(bcf.config.keepAliveInterval)
  )

  private val consumerSettings : BlendedSingleConnectionFactory => JmsConsumerSettings = bcf => JmsConsumerSettings(
    log = envLogger,
    headerCfg = FlowHeaderConfig.create(ctCtxt),
    connectionFactory = bcf,
    jmsDestination = Some(JmsDestination.create(bcf.config.keepAliveDestination).get),
    logLevel = _ => LogLevel.Debug,
    acknowledgeMode = AcknowledgeMode.AutoAcknowledge,
    selector = Some(s"JMSCorrelationID = '${corrId}'"),
    ackTimeout = 1.second
  )

  private val producer : BlendedSingleConnectionFactory => Sink[FlowEnvelope, NotUsed] = bcf => jmsProducer(
      name = s"KeepAlive-send-${bcf.vendor}-${bcf.provider}",
      settings = producerSettings(bcf),
      autoAck = true
    )
    .to(Sink.ignore)

  private val consumer: BlendedSingleConnectionFactory => Source[FlowEnvelope, NotUsed] = bcf => jmsConsumer(
    name = s"KeepAlive-Rec-${bcf.vendor}-${bcf.provider}",
    settings = consumerSettings(bcf),
    minMessageDelay = None
  )

  // scalastyle:off magic.number
  val keepAliveSource: BlendedSingleConnectionFactory => Source[FlowEnvelope, ActorRef] = bcf =>
    StreamFactories.actorSource[FlowEnvelope](10, OverflowStrategy.dropBuffer)
      .viaMat(Flow.fromSinkAndSourceCoupled(producer(bcf), consumer(bcf)))(Keep.left)
  // scalastyle:on magic.number

  override def start(): Unit = {
    stream = Some(system.actorOf(
      StreamController.props[FlowEnvelope, ActorRef](
        s"KeepAlive-stream-${cf.vendor}-${cf.provider}",
        keepAliveSource(cf),
        streamsCfg
      )(onMaterialize = { actor =>
        system.eventStream.publish(ProducerMaterialized(cf.vendor, cf.provider, actor))
      })
    ))
  }

  override def stop(): Unit = {
    stream.foreach(system.stop)
    stream = None
  }
}
