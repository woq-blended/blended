package blended.akka.http.restjms.internal

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{IdAwareConnectionFactory, JmsQueue}
import blended.streams.jms.{JmsConsumerSettings, JmsConsumerStage, JmsEnvelopeHeader, JmsProducerSettings, JmsProducerStage, MessageDestinationResolver}
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig, FlowProcessor, StreamController}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.util.Try

object MockResponses {

  val json = """{ result: "redeem" }"""

  val xml =
    """<?xml version="1.0" encoding="UTF-8" ?>
      |<result>
      |  <value>redeem</value>
      |</result>""".stripMargin
}

class JMSResponder(
  cf : IdAwareConnectionFactory, idSvc : ContainerIdentifierService
)(implicit system : ActorSystem, materializer : ActorMaterializer) extends JmsEnvelopeHeader {

  private val log : Logger = Logger[JMSResponder]
  private var streamActor : Option[ActorRef] = None
  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(idSvc)

  private val consumerSettings : JmsConsumerSettings = JmsConsumerSettings(
    log = log,
    headerCfg = headerCfg,
    connectionFactory = cf,
    jmsDestination = Some(JmsQueue("redeem"))
  )

  private val producerSettings : JmsProducerSettings = JmsProducerSettings(
    log = log,
    headerCfg = headerCfg,
    connectionFactory = cf,
    destinationResolver = s => new MessageDestinationResolver(s)
  )

  def start() : Unit = {
    val src : Source[FlowEnvelope, NotUsed] =
      Source.fromGraph(new JmsConsumerStage("requestor-src", consumerSettings, None))
      .via(FlowProcessor.fromFunction("respond", log){ env => Try {
        val body : String = env.header[String]("Content-Type") match {
          case Some("text/xml")   => MockResponses.xml
          case Some("application/json") => MockResponses.json
          case u => throw new Exception(s"Unsupported content type for backend [${u}]")
        }

        val replyTo : String = env.header[String](replyToHeader(headerCfg.prefix)).get

        env.copy(flowMessage =
          FlowMessage(body)(env.flowMessage.header))
          .withHeader(destHeader(headerCfg.prefix), replyTo).get

      }})
      .via(Flow.fromGraph(new JmsProducerStage("requestor-respond", producerSettings)))

    val streamCfg : BlendedStreamsConfig = new BlendedStreamsConfig {
      override def transactionShard: Option[String] = None
      override def minDelay: FiniteDuration = 1.second
      override def maxDelay: FiniteDuration = 1.second
      override def exponential: Boolean = false
      override def random: Double = 0.1
      override def onFailureOnly: Boolean = true
      override def resetAfter: FiniteDuration = 1.second
    }
    log.info("Starting JMS Responder")

    streamActor = Some(system.actorOf(StreamController.props[FlowEnvelope, NotUsed]("jmsResponder", src, streamCfg)(onMaterialize = _ => {} )))
  }

  def stop(): Unit = {
    streamActor.foreach { a =>
      a ! StreamController.Stop
      a ! PoisonPill
    }
  }
}