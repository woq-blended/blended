package blended.samples.camel.internal

import javax.jms.ConnectionFactory

import blended.camel.utils.BlendedCamelContext
import domino.DominoActivator
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jms.JmsComponent
import org.slf4j.LoggerFactory

class CamelSampleActivator extends DominoActivator {

  whenBundleActive {

    val log = LoggerFactory.getLogger(classOf[CamelSampleActivator])
    whenAdvancedServicePresent[ConnectionFactory]("(provider=activemq)") { cf =>

      val ctxt = BlendedCamelContext(name = "BlendedSampleContext", withJmx = true, idSvc = None)
      ctxt.addComponent("activemq", JmsComponent.jmsComponent(cf))

      ctxt.addRoutes(new RouteBuilder() {
        override def configure(): Unit = {
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
