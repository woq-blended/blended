package blended.samples.jms.internal

import java.util.concurrent.atomic.AtomicLong
import javax.jms.ConnectionFactory

import blended.camel.utils.BlendedCamelContextFactory
import blended.container.context.ContainerIdentifierService
import blended.domino.TypesafeConfigWatching
import domino.DominoActivator
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jms.JmsComponent
import org.apache.camel.{Exchange, Processor}
import org.slf4j.LoggerFactory

object JmsSampleActivator {
  val msgCounter : AtomicLong = new AtomicLong()
}

class JmsSampleActivator extends DominoActivator with TypesafeConfigWatching {
  import JmsSampleActivator.msgCounter

  private[this] val log = LoggerFactory.getLogger(classOf[JmsSampleActivator])

  whenBundleActive {
    whenTypesafeConfigAvailable { (cfg, idSvc) =>
      val jmsSampleCfg = JmsSampleConfig(cfg)

      whenAdvancedServicePresent[ConnectionFactory]("(provider=activemq)"){ cf =>
        whenServicePresent[ContainerIdentifierService] { idSvc =>
          val ctxt = BlendedCamelContextFactory.createContext(name = "JmsSampleContext", withJmx = true, idSvc = idSvc)
          ctxt.addComponent("activemq", JmsComponent.jmsComponent(cf))

          ctxt.addRoutes(new RouteBuilder() {
            override def configure(): Unit = {

              if (jmsSampleCfg.producerInterval > 0) {
                log.info(s"Creating producer with interval [${jmsSampleCfg.producerInterval}]ms for destination [${jmsSampleCfg.destination}]")
                from(s"timer:sample?period=${jmsSampleCfg.producerInterval}")
                  .routeId("sampleProducer")
                  .process(new Processor() {
                    override def process(exchange: Exchange): Unit = {
                      exchange.setOut(exchange.getIn())
                      exchange.getOut().setHeader("SampleCounter", msgCounter.getAndIncrement().toString())
                    }
                  })
                  .to(s"activemq:${jmsSampleCfg.destination}")
              }

              jmsSampleCfg.consumeSelector.foreach { sel =>
                log.info(s"Creating consumer for destination [${jmsSampleCfg.destination}] with selector [$sel]")

                var endpoint = "activemq:" + jmsSampleCfg.destination

                if (sel.trim().length() > 0) endpoint = endpoint + "?selector=" + sel

                from(endpoint)
                  .routeId("sampleConsumer")
                  .to("log:jmsSample?showHeaders=true&maxChars=500")
              }
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
  }
}

