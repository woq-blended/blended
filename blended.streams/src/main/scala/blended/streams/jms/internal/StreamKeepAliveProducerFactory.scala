package blended.streams.jms.internal

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import blended.container.context.api.ContainerContext
import blended.jms.utils.{BlendedSingleConnectionFactory, JmsDestination, ProducerMaterialized}
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig, FlowProcessor, StreamController}
import blended.util.logging.LogLevel

class StreamKeepAliveProducerFactory(
  log : BlendedSingleConnectionFactory => FlowEnvelopeLogger,
  ctCtxt : ContainerContext,
  streamsCfg : BlendedStreamsConfig
)(implicit system: ActorSystem, materializer : Materializer) extends KeepAliveProducerFactory with JmsStreamSupport {

  private var stream : Option[ActorRef] = None

  private val corrId : BlendedSingleConnectionFactory => String = bcf => s"${ctCtxt.uuid}-${bcf.vendor}-${bcf.provider}"

  private val producerSettings : BlendedSingleConnectionFactory => JmsProducerSettings = bcf => JmsProducerSettings(
    log = log(bcf),
    headerCfg = FlowHeaderConfig.create(ctCtxt),
    connectionFactory = bcf,
    jmsDestination = Some(JmsDestination.create(bcf.config.keepAliveDestination).get),
    timeToLive = Some(bcf.config.keepAliveInterval)
  )

  private val consumerSettings : BlendedSingleConnectionFactory => JmsConsumerSettings = bcf => JmsConsumerSettings(
    log = log(bcf),
    headerCfg = FlowHeaderConfig.create(ctCtxt),
    connectionFactory = bcf,
    jmsDestination = Some(JmsDestination.create(bcf.config.keepAliveDestination).get),
    logLevel = _ => LogLevel.Debug,
    acknowledgeMode = AcknowledgeMode.AutoAcknowledge,
    selector = Some(s"JMSCorrelationID = '${corrId(bcf)}'")
  )

  private val setHeader : BlendedSingleConnectionFactory => Flow[FlowEnvelope, FlowEnvelope, NotUsed] = bcf => Flow.fromGraph(
    FlowProcessor.fromFunction("setHeader", log(bcf)){ env => {
      env.withHeader("JMSCorrelationID", corrId(bcf))
    }}
  )

  private val producer : BlendedSingleConnectionFactory => Sink[FlowEnvelope, NotUsed] = bcf => setHeader(bcf)
    .via(
      jmsProducer(
        name = s"KeepAlive-send-${bcf.vendor}-${bcf.provider}",
        settings = producerSettings(bcf),
        autoAck = true
      )
    )
    .to(Sink.ignore)

  private val consumer: BlendedSingleConnectionFactory => Source[FlowEnvelope, NotUsed] = bcf => jmsConsumer(
    name = s"KeepAlive-Rec-${bcf.vendor}-${bcf.provider}",
    settings = consumerSettings(bcf),
    minMessageDelay = None
  )

  // scalastyle:off magic.number
  val keepAliveSource: BlendedSingleConnectionFactory => Source[FlowEnvelope, ActorRef] = bcf => Source.actorRef(
    10, OverflowStrategy.dropBuffer
  ).viaMat(Flow.fromSinkAndSourceCoupled(producer(bcf), consumer(bcf)))(Keep.left)
  // scalastyle:on magic.number

  override def start(bcf : BlendedSingleConnectionFactory): Unit = {
    stream = Some(system.actorOf(
      StreamController.props[FlowEnvelope, ActorRef](
        s"KeepAlive-stream-${bcf.vendor}-${bcf.provider}",
        keepAliveSource(bcf),
        streamsCfg
      )(onMaterialize = { actor =>
        system.eventStream.publish(ProducerMaterialized(bcf.vendor, bcf.provider, actor))
      })
    ))
  }

  override def stop(): Unit = stream.foreach(system.stop)
}
