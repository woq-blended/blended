package blended.akka.http.proxy.internal

import domino.DominoActivator
import blended.domino.TypesafeConfigWatching
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import scala.concurrent.duration._
import akka.util.Timeout
import blended.akka.ActorSystemWatching
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.headers.Host
import javax.net.ssl.SSLContext
import blended.akka.http.SimpleHttpContext
import blended.akka.http.HttpContext

class BlendedAkkaHttpProxyActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      log.debug("Given configuration: " + cfg.config)

      // read config
      val config = ProxyConfig.parse(cfg.config)
      log.debug(s"Parsed configuration: [${config}]")
      config.failed.foreach { e =>
        log.error(e)("Config parse error")
      }

      // handle each configured proxy endpoint independently
      config.get.paths.foreach { proxyTarget =>
        // setup proxys route according to config and register it into the service registry
        val proxyConfig = proxyTarget.copy(uri = cfg.idSvc.resolvePropertyString(proxyTarget.uri).get)
        log.debug(s"About to setup proxy [${proxyConfig}]")

        val sslContextFilter = "(type=client)"

        def register(route: ProxyRoute): Unit = {
          log.debug(s"Registering proxy route [${proxyConfig}]")
          SimpleHttpContext(s"proxy/${proxyConfig.path}", route.proxyRoute).providesService[HttpContext]
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