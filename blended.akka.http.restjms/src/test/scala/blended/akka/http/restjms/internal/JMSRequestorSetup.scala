package blended.akka.http.restjms.internal

import com.typesafe.config.{Config, ConfigFactory}
import javax.jms.ConnectionFactory
import org.apache.activemq.ActiveMQConnectionFactory

object MockResponses {

  val json = """{ result: "redeem" }"""

  val xml =
    """<?xml version="1.0" encoding="UTF-8" ?>
      |<result>
      |  <value>redeem</value>
      |</result>""".stripMargin
}

trait JMSRequestorSetup {

  val amqCF : ConnectionFactory = new ActiveMQConnectionFactory("vm://dispatcher?broker.useJmx=false&broker.persistent=false&create=true")
  val cfg : Config = ConfigFactory.load("restjms.conf")
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
