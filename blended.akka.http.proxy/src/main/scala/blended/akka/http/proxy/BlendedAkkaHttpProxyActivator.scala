package blended.akka.http.proxy

import blended.akka.ActorSystemWatching
import blended.akka.http.proxy.internal.{ProxyConfig, ProxyRoute, SimpleProxyRoute}
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.util.logging.Logger
import domino.DominoActivator
import javax.net.ssl.SSLContext

class BlendedAkkaHttpProxyActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = Logger[BlendedAkkaHttpProxyActivator]

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      log.debug("Given configuration: " + cfg.config)

      // read config
      val config = ProxyConfig.parse(cfg.config)
      log.debug(s"Parsed configuration: [$config]")
      config.failed.foreach { e =>
        log.error(e)("Config parse error")
      }

      val context = config.get.context

      // handle each configured proxy endpoint independently
      config.get.paths.foreach { proxyTarget =>
        // setup proxys route according to config and register it into the service registry
        val proxyConfig = proxyTarget.copy(uri = cfg.idSvc.resolvePropertyString(proxyTarget.uri).map(_.toString()).get)
        log.debug(s"About to setup proxy [${proxyConfig}]")

        val sslContextFilter = "(type=client)"

        def register(route : ProxyRoute) : Unit = {
          log.debug(s"Registering proxy route [${proxyConfig}] at [$context/${proxyConfig.path}]")
          SimpleHttpContext(s"$context/${proxyConfig.path}", route.proxyRoute).providesService[HttpContext]
          onStop {
            log.debug(s"Unregistering proxy route [${proxyConfig}]")
          }
        }

        if (proxyConfig.isHttps || proxyConfig.redirectCount > 0) {
          // in case we want to use HTTPS or we follow redirects (which can be HTTPS)
          // SSLContext isn't optional
          log.debug(s"Watching for client SSLContext before creating proxy: ${proxyConfig}")
          whenAdvancedServicePresent[SSLContext](sslContextFilter) { sslContext =>
            val proxyRoute = new SimpleProxyRoute(proxyConfig, cfg.system, Some(sslContext))
            register(proxyRoute)
            onStop {
              log.debug(s"Detected SSLContext deregistration.")
            }
          }
        } else {
          // SSLContext is optional, but we try to consume it nevertheless
          val proxyRoute = new SimpleProxyRoute(proxyConfig, cfg.system, service[SSLContext](sslContextFilter))
          register(proxyRoute)
        }

      }
    }
  }

}
