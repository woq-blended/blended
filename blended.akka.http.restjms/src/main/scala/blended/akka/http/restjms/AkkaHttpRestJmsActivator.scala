package blended.akka.http.restjms

import blended.akka.ActorSystemWatching
import blended.akka.http.restjms.internal.SimpleRestJmsService
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.jms.bridge.BridgeProviderRegistry
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.BlendedStreamsConfig
import domino.DominoActivator

class AkkaHttpRestJmsActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>

      val webContext : String = cfg.config.getString("webcontext")

      whenServicePresent[BlendedStreamsConfig]{ streamsCfg =>
        whenServicePresent[BridgeProviderRegistry] { registry =>
          val vendor = registry.internalProvider.get.vendor
          val provider = registry.internalProvider.get.provider
          whenAdvancedServicePresent[IdAwareConnectionFactory](s"(&(vendor=$vendor)(provider=$provider))") { cf =>

            val svc = new SimpleRestJmsService(
              name = webContext,
              osgiCfg = cfg,
              streamsConfig = streamsCfg,
              registry = registry,
              cf = cf
            )

            svc.start()

            onStop {
              svc.stop()
            }

            SimpleHttpContext(webContext, svc.httpRoute).providesService[HttpContext]
          }
        }
      }
    }
  }
}
