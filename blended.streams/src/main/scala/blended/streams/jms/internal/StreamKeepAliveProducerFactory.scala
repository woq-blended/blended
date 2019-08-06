package blended.streams.jms.internal

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{BlendedSingleConnectionFactory, JmsDestination}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.FlowHeaderConfig
import blended.streams.{FlowProcessor, StreamController, StreamControllerConfig}
import blended.util.logging.{LogLevel, Logger}

import scala.concurrent.{Future, Promise}
import scala.util.Success

class StreamKeepAliveProducerFactory(
  log : BlendedSingleConnectionFactory => Logger,
  idSvc : ContainerIdentifierService
)(implicit system: ActorSystem, materializer : Materializer) extends KeepAliveProducerFactory with JmsStreamSupport {

  private var stream : Option[ActorRef] = None

  private val producerSettings : BlendedSingleConnectionFactory => JmsProducerSettings = bcf =>
    JmsProducerSettings(
      log = log(bcf),
      headerCfg = FlowHeaderConfig.create(idSvc),
      connectionFactory = bcf,
      jmsDestination = Some(JmsDestination.create(bcf.config.pingDestination).get),
      timeToLive = Some(bcf.config.keepAliveInterval)
    )

  private val consumerSettings : BlendedSingleConnectionFactory => JMSConsumerSettings = bcf =>
    JMSConsumerSettings(
      log = log(bcf),
      headerCfg = FlowHeaderConfig.create(idSvc),
      connectionFactory = bcf,
      jmsDestination = Some(JmsDestination.create(bcf.config.pingDestination).get),
      receiveLogLevel = LogLevel.Debug,
      acknowledgeMode = AcknowledgeMode.AutoAcknowledge,
      selector = Some(s"JMSCorrelationID = '${idSvc.uuid}'")
    )


  override val createProducer: BlendedSingleConnectionFactory => Future[ActorRef] = { bcf =>

    val futMat : Promise[ActorRef] = Promise[ActorRef]

    val setHeader : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
      FlowProcessor.fromFunction("setHeader", log(bcf)){ env => {
      env.withHeader("JMSCorrelationID", idSvc.uuid)
    }})

    val producer: Sink[FlowEnvelope, NotUsed] = setHeader
      .via(
        jmsProducer(
          name = s"KeepAlive-send-${bcf.vendor}-${bcf.provider}",
          settings = producerSettings(bcf),
          autoAck = true
        )
      )
      .to(Sink.ignore)

    val consumer: Source[FlowEnvelope, NotUsed] = jmsConsumer(
      name = s"KeepAlive-Rec-${bcf.vendor}-${bcf.provider}",
      settings = consumerSettings(bcf),
      minMessageDelay = None
    )

    // scalastyle:off magic.number
    val keepAliveSource: Source[FlowEnvelope, ActorRef] = Source.actorRef(
      10, OverflowStrategy.dropBuffer
    ).viaMat(Flow.fromSinkAndSourceCoupled(producer, consumer))(Keep.left)
    // scalastyle:on magic.number

    val streamCfg: StreamControllerConfig = StreamControllerConfig(
      name = s"KeepAlive-stream-${bcf.vendor}-${bcf.provider}",
      minDelay = bcf.config.keepAliveInterval,
      maxDelay = bcf.config.keepAliveInterval,
      exponential = false,
      onFailureOnly = true,
      random = 0.2
    )

    stream = Some(system.actorOf(
      StreamController.props[FlowEnvelope, ActorRef](keepAliveSource, streamCfg)(onMaterialize = { actor =>
        futMat.complete(Success(actor))
      })
    ))

    futMat.future
  }

  override def stop(): Unit = stream.foreach(system.stop)
}
