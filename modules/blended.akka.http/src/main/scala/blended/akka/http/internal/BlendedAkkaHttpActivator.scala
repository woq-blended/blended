package blended.akka.http.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http}
import blended.akka.ActorSystemWatching
import blended.akka.http.SimpleHttpContext
import blended.util.config.Implicits._
import blended.util.logging.Logger
import domino.DominoActivator
import javax.net.ssl.SSLContext

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import blended.jmx.ProductMBeanManager
import blended.jmx.NamingStrategy
import blended.jmx.NamingStrategyResolver

class BlendedAkkaHttpActivator extends DominoActivator
  with ActorSystemWatching {

  private[this] val log : Logger = Logger[BlendedAkkaHttpActivator]
  private[this] val defaultHttpPort : Int = 8080
  private[this] val defaultHttpsPort : Int = 8443

  whenBundleActive {
    whenServicePresent[ProductMBeanManager] { mbeanMgr =>

      val jmxSupport : AkkaHttpServerJmxSupport = new AkkaHttpServerJmxSupport(mbeanMgr)
      new AkkaHttpNamingStrategy().providesService[NamingStrategy](NamingStrategyResolver.strategyClassNameProp -> classOf[AkkaHttpServerInfo].getName())

      // reuse the blended akka system
      whenActorSystemAvailable { cfg =>

        val config = cfg.config

        val httpHost = config.getString("host", "0.0.0.0")
        val httpPort = config.getInt("port", defaultHttpPort)

        val httpsHost = config.getString("ssl.host", "0.0.0.0")
        val httpsPort = config.getInt("ssl.port", defaultHttpsPort)

        implicit val actorSystem : ActorSystem = cfg.system
        // needed for the future flatMap/onComplete in the end
        implicit val executionContext : ExecutionContext = actorSystem.dispatcher

        val dynamicRoutes = new RouteProvider(Some(jmxSupport))

        log.info(s"Starting HTTP server at [$httpHost:$httpPort]")
        val bindingFuture = Http().bindAndHandle(dynamicRoutes.dynamicRoute, httpHost, httpPort)

        bindingFuture.onComplete {
          case Success(b) =>
            jmxSupport.updateInJmx(info => info.withHost(b.localAddress.getHostString()).withPort(b.localAddress.getPort()))
            log.info(s"Started HTTP server at ${b.localAddress}")
          case Failure(t) =>
            log.error(t)(s"Failed to start HTTP Server : [${t.getMessage()}]")
            throw t
        }

        onStop {
          log.info(s"Stopping HTTP server at [$httpHost:$httpPort]")
          bindingFuture.map(serverBinding => serverBinding.unbind())
        }

        log.debug("Listening for SSLContext registrations of type=server...")
        whenAdvancedServicePresent[SSLContext]("(type=server)") { sslContext =>

          log.info(s"Detected an server SSLContext. Starting HTTPS server at [$httpsHost:$httpsPort]")

          val https = ConnectionContext.https(sslContext)
          val httpsBindingFuture = Http().bindAndHandle(
            handler = dynamicRoutes.dynamicRoute,
            interface = httpsHost,
            port = httpsPort,
            connectionContext = https
          )

          httpsBindingFuture.onComplete {
            case Success(b) =>
              log.info(s"Started HTTPS server at ${b.localAddress}")
              jmxSupport.updateInJmx(info => info.withSslHost(b.localAddress.getHostString()).withSslPort(b.localAddress.getPort()))
            case Failure(t) =>
              log.error(t)(t.getMessage())
              throw t
          }

          onStop {
            log.info(s"Stopping HTTPS server at [$httpsHost:$httpsPort]")
            httpsBindingFuture.map(serverBinding => serverBinding.unbind())
            jmxSupport.updateInJmx(_.clearSslAddress())
          }
        }

        // Consume routes from OSGi Service Registry (white-board pattern)
        log.info(s"Listening for routes packaged in [${classOf[SimpleHttpContext].getSimpleName()}]")
        dynamicRoutes.dynamicAdapt(capsuleContext, bundleContext)
      }
    }
  }
}
