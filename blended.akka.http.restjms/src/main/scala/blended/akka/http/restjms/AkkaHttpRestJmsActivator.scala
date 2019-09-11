package blended.akka.http.restjms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.akka.ActorSystemWatching
import blended.akka.http.restjms.internal.{RestJMSConfig, SimpleRestJmsService}
import blended.akka.http.{HttpContext, SimpleHttpContext}
import domino.DominoActivator
import javax.jms.ConnectionFactory

class AkkaHttpRestJmsActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>

      val vendor = cfg.config.getString("vendor")
      val provider = cfg.config.getString("provider")

      whenAdvancedServicePresent[ConnectionFactory](s"(&(vendor=$vendor)(provider=$provider))") { cf =>

        implicit val as : ActorSystem = cfg.system
        val materializer = ActorMaterializer()
        val eCtxt = cfg.system.dispatcher

        val operations = RestJMSConfig.fromConfig(cfg.config).operations
        val svc = new SimpleRestJmsService(operations, materializer, eCtxt)

        SimpleHttpContext(cfg.config.getString("webcontext"), svc.httpRoute).providesService[HttpContext]
      }
    }
  }
}
