package blended.streams.jms.internal

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{BlendedSingleConnectionFactory, JmsDestination, ProducerMaterialized}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig, FlowProcessor, StreamController}
import blended.util.logging.{LogLevel, Logger}

import scala.concurrent.Future

class StreamKeepAliveProducerFactory(
  log : BlendedSingleConnectionFactory => Logger,
  idSvc : ContainerIdentifierService,
  streamsCfg : BlendedStreamsConfig
)(implicit system: ActorSystem, materializer : Materializer) extends KeepAliveProducerFactory with JmsStreamSupport {

  private var stream : Option[ActorRef] = None

  private val producerSettings : BlendedSingleConnectionFactory => JmsProducerSettings = bcf => JmsProducerSettings(
    log = log(bcf),
    headerCfg = FlowHeaderConfig.create(idSvc),
    connectionFactory = bcf,
    jmsDestination = Some(JmsDestination.create(bcf.config.pingDestination).get),
    timeToLive = Some(bcf.config.keepAliveInterval)
  )

  private val consumerSettings : BlendedSingleConnectionFactory => JMSConsumerSettings = bcf => JMSConsumerSettings(
    log = log(bcf),
    headerCfg = FlowHeaderConfig.create(idSvc),
    connectionFactory = bcf,
    jmsDestination = Some(JmsDestination.create(bcf.config.pingDestination).get),
    receiveLogLevel = LogLevel.Debug,
    acknowledgeMode = AcknowledgeMode.AutoAcknowledge,
    selector = Some(s"JMSCorrelationID = '${idSvc.uuid}'")
  )

  private val setHeader : BlendedSingleConnectionFactory => Flow[FlowEnvelope, FlowEnvelope, NotUsed] = bcf => Flow.fromGraph(
    FlowProcessor.fromFunction("setHeader", log(bcf)){ env => {
      env.withHeader("JMSCorrelationID", idSvc.uuid)
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
        log(bcf).debug(s"Keep alive Stream for [${bcf.vendor}:${bcf.provider}] materialized.")
        system.eventStream.publish(ProducerMaterialized(bcf.vendor, bcf.provider, actor))
      })
    ))
  }

  override def stop(): Unit = stream.foreach(system.stop)
}
