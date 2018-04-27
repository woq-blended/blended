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

class BlendedAkkaHttpProxyActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      //    whenTypesafeConfigAvailable { (cfg, idService) =>
      log.debug("Configuration: " + cfg.config)

      //      implicit val actorSystem = cfg.system
      //      implicit val materializer = ActorMaterializer()
      //      import scala.concurrent.ExecutionContext.Implicits.global

      // read config
      val config = ProxyConfig.parse(cfg.config).get

      // do we need client ssl support?
      //      config.paths.find(p => p.isSsl)

      // handle each configured proxy endpoint independently
      config.paths.foreach { proxyTarget =>
        // setup proxys route according to config and register it into the service registry
        val proxyConfig = proxyTarget.copy(uri = cfg.idSvc.resolvePropertyString(proxyTarget.uri).get)
        //        val baseUri: String = cfg.idSvc.resolvePropertyString(proxy.uri).get

        if (proxyConfig.isHttps) {
          log.debug(s"Watching for cliene SSLContext to create proxy: ${proxyConfig}")
          whenAdvancedServicePresent[SSLContext]("(type=server)") { sslContext =>
            val proxyRoute = new SimpleProxyRoute(proxyConfig, cfg.system, Some(sslContext))
            proxyRoute.proxyRoute.providesService[Route]
          }
        } else {
          val proxyRoute = new SimpleProxyRoute(proxyConfig, cfg.system)
          proxyRoute.proxyRoute.providesService[Route]
        }

        //
        //        val proxyRoute: Route = {
        //          path(proxy.path / Remaining) { requestPath => ctx =>
        //            val request = ctx.request
        //
        //            // Keep the query part of the original request
        //            val uri = Uri(
        //              if (requestPath.isEmpty) baseUri
        //              else s"$baseUri/${requestPath}"
        //            ).copy(rawQueryString = request.uri.rawQueryString)
        //
        //            // use the timeout of the config
        //            implicit val timeout = Timeout(proxy.timeout.seconds)
        //
        //            log.info(s"Received HttpRequest [${request}] at endpoint [${proxy.path}] and path [${requestPath}] with query [${request.uri.queryString()}]")
        //            // outgoing connection uses ip and port from the configured uri
        //            val flow = Http().outgoingConnection(uri.authority.host.address(), uri.authority.port)
        //
        //            // keep headers, but not the host header
        //            val headers = request.headers.filter(header => header.isNot(Host.lowercaseName))
        //
        //            // the final request to the target host
        //            val proxyReq = HttpRequest(method = request.method, uri = uri, entity = request.entity).withHeaders(headers)
        //
        //            val handler = Source.single(proxyReq).
        //              via(flow).
        //              runWith(Sink.head).
        //              flatMap(r => ctx.complete(r))
        //
        //            handler
        //          }
        //        }
      }
    }
  }

}