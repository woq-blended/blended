package blended.akka.http.restjms.internal

import blended.camel.utils.BlendedCamelContextFactory
import com.typesafe.config.ConfigFactory
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.camel.CamelContext
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jms.JmsComponent

trait JMSRequestorSetup {

  val amqCF = new ActiveMQConnectionFactory("vm://dispatcher?broker.useJmx=false&broker.persistent=false&create=true")
  val cfg = ConfigFactory.load("restjms.conf")
  val restJmsConfig = RestJMSConfig.fromConfig(cfg)

  val camelContext: CamelContext = {
    val result = BlendedCamelContextFactory.createContext(withJmx = false)

    result.addComponent("jms", JmsComponent.jmsComponent(amqCF))

    result.addRoutes(new RouteBuilder() {
      override def configure(): Unit = {
        from("jms:queue:redeem")
          .to("log:redeem?showHeaders=true")
          .setBody(constant("redeemed"))
      }
    })

    result.start()

    result
  }
}
