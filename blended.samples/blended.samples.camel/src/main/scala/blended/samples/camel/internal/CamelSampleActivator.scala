package blended.samples.camel.internal

import blended.camel.utils.BlendedCamelContextFactory
import blended.util.logging.Logger
import domino.DominoActivator
import javax.jms.ConnectionFactory
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jms.JmsComponent

class CamelSampleActivator extends DominoActivator {

  whenBundleActive {

    val log = Logger[CamelSampleActivator]
    whenAdvancedServicePresent[ConnectionFactory]("(provider=activemq)") { cf =>

      val ctxt = BlendedCamelContextFactory.createContext(name = "BlendedSampleContext", withJmx = true)
      ctxt.addComponent("activemq", JmsComponent.jmsComponent(cf))

      ctxt.addRoutes(new RouteBuilder() {
        override def configure() : Unit = {
          from("activemq:queue:SampleIn").id("SampleRoute")
            .setHeader("Description", constant("BlendedSample"))
            .to("activemq:queue:SampleOut")
        }
      })

      ctxt.start()

      onStop {
        log.debug("Stopping Camel Context")
        ctxt.stop()
      }
    }
  }
}
