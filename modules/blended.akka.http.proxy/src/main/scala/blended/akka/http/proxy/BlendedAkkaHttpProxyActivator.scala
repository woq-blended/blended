package blended.akka.http.proxy

import blended.akka.ActorSystemWatching
import blended.akka.http.proxy.internal.{ProxyConfig, ProxyRoute, SimpleProxyRoute}
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.util.logging.Logger
import domino.DominoActivator
import javax.net.ssl.SSLContext

import scala.util.{Failure, Success}

class BlendedAkkaHttpProxyActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = Logger[BlendedAkkaHttpProxyActivator]

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      log.debug("Given configuration: " + cfg.config)

      // read config
      ProxyConfig.parse(cfg.config) match {
        case Failure(e) =>
          log.error(e)(s"Unable to parse config : [${e.getMessage()}]")
        case Success(config) =>
          log.debug(s"Parsed configuration: [$config]")

          val context = config.context

      // handle each configured proxy endpoint independently
      config.paths.foreach { proxyTarget =>
        // setup proxys route according to config and register it into the service registry
        val proxyConfig = proxyTarget.copy(
          uri = cfg.ctContext.resolveString(proxyTarget.uri).map(_.toString()).get,
          user = proxyTarget.user.map(s => cfg.ctContext.resolveString(s).map(_.toString()).get),
          password = proxyTarget.password.map(s => cfg.ctContext.resolveString(s).map(_.toString()).get),
        )
        log.debug(s"About to setup proxy [${proxyConfig}]")

            val sslContextFilter = "(type=client)"

            def register(route: ProxyRoute): Unit = {
              log.debug(s"Registering proxy route [$proxyConfig] at [$context/${proxyConfig.path}]")
              SimpleHttpContext(s"$context/${proxyConfig.path}", route.proxyRoute).providesService[HttpContext]
              onStop {
                log.debug(s"Unregistering proxy route [$proxyConfig]")
              }
            }

            if (proxyConfig.isHttps || proxyConfig.redirectCount > 0) {
              // in case we want to use HTTPS or we follow redirects (which can be HTTPS)
              // SSLContext isn't optional
              log.debug(s"Watching for client SSLContext before creating proxy: [$proxyConfig]")
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
}
