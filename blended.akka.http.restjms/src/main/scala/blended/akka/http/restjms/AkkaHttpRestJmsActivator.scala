package blended.akka.http.restjms

import blended.akka.ActorSystemWatching
import blended.akka.http.restjms.internal.SimpleRestJmsService
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.BlendedStreamsConfig
import domino.DominoActivator

class AkkaHttpRestJmsActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>

      val vendor = cfg.config.getString("vendor")
      val provider = cfg.config.getString("provider")

      val webContext : String = cfg.config.getString("webcontext")

      whenServicePresent[BlendedStreamsConfig]{ streamsCfg =>
        whenAdvancedServicePresent[IdAwareConnectionFactory](s"(&(vendor=$vendor)(provider=$provider))") { cf =>

          val svc = new SimpleRestJmsService(
            name = webContext,
            osgiCfg = cfg,
            streamsConfig = streamsCfg,
            cf = cf
          )

          svc.start()

          onStop{
            svc.stop()
          }

          SimpleHttpContext(webContext, svc.httpRoute).providesService[HttpContext]
        }
      }
    }
  }
}
