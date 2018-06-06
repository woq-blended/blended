package blended.akka.http.restjms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.akka.http.restjms.internal.{RestJMSConfig, SimpleRestJmsService}
import blended.camel.utils.BlendedCamelContextFactory
import domino.DominoActivator
import javax.jms.ConnectionFactory
import org.apache.camel.CamelContext
import org.apache.camel.component.jms.JmsComponent

class AkkaHttpRestJmsActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable {cfg =>

      val vendor = cfg.config.getString("vendor")
      val provider = cfg.config.getString("provider")

      whenAdvancedServicePresent[ConnectionFactory](s"&(vendor=$vendor)(provider=$provider)") { cf =>
        val cCtxt : CamelContext = {
          val answer = BlendedCamelContextFactory.createContext(
            name = cfg.bundleContext.getBundle().getSymbolicName(),
            withJmx = false,
            idSvc = cfg.idSvc
          )

          answer.addComponent("jms", JmsComponent.jmsComponent(cf))
          answer.start()

          answer
        }

        implicit val as : ActorSystem = cfg.system
        val materializer = ActorMaterializer()
        val eCtxt = cfg.system.dispatcher

        val operations = RestJMSConfig.fromConfig(cfg.config).operations
        val svc = new SimpleRestJmsService(operations, cCtxt, materializer, eCtxt)

        SimpleHttpContext(cfg.config.getString("context"), svc.httpRoute).providesService[HttpContext]

        onStop {
          cCtxt.stop()
        }
      }
    }
  }
}
