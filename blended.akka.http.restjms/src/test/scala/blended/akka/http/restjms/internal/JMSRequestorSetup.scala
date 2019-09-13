package blended.akka.http.restjms.internal

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import blended.jms.utils.{IdAwareConnectionFactory, JmsQueue}
import blended.streams.jms.{JmsConsumerSettings, JmsConsumerStage, JmsProducerSettings, MessageDestinationResolver}
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig, StreamController}
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory}
import javax.jms.ConnectionFactory
import org.apache.activemq.ActiveMQConnectionFactory

import scala.concurrent.duration._

object MockResponses {

  val json = """{ result: "redeem" }"""

  val xml =
    """<?xml version="1.0" encoding="UTF-8" ?>
      |<result>
      |  <value>redeem</value>
      |</result>""".stripMargin
}

class JMSResponder(cf : IdAwareConnectionFactory)(implicit system : ActorSystem, materializer : ActorMaterializer) {

  private val log : Logger = Logger[JMSResponder]
  private var streamActor : Option[ActorRef] = None
  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create("App")

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
    val src : Source[FlowEnvelope, NotUsed] = Source.fromGraph(new JmsConsumerStage("src-requestor", consumerSettings, None))
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

trait JMSRequestorSetup {

  val amqCF : ConnectionFactory = new ActiveMQConnectionFactory("vm://dispatcher?broker.useJmx=false&broker.persistent=false&create=true")
  val cfg : Config = ConfigFactory.load("container/etc/restjms.conf")
  val restJmsConfig : RestJMSConfig = RestJMSConfig.fromConfig(cfg)

//  val camelContext : CamelContext = {
//    val result = BlendedCamelContextFactory.createContext(withJmx = false)
//
//    result.addComponent("jms", JmsComponent.jmsComponent(amqCF))
//
//    result.addRoutes(new RouteBuilder() {
//      override def configure() : Unit = {
//        from("jms:queue:redeem")
//          .to("log:redeem?showHeaders=true")
//          .process(new Processor {
//            override def process(exchange : Exchange) : Unit = {
//              val responseBody = Option(exchange.getIn().getHeader("Content-Type", classOf[String])) match {
//                case None => throw new Exception("Content-Type is not defined.")
//                case Some(s) => s match {
//                  case "text/xml"         => MockResponses.xml
//                  case "application/json" => MockResponses.json
//                  case u                  => throw new Exception(s"Unsupported content type for backend [${u}]")
//                }
//              }
//
//              exchange.setOut(exchange.getIn())
//              exchange.getOut().setBody(responseBody)
//            }
//          })
//      }
//    })
//
//    result.start()
//
//    result
//  }
}
