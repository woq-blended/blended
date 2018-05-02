package blended.akka.http.proxy.internal

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import scala.concurrent.duration._
import akka.stream.scaladsl.Sink
import akka.http.scaladsl.Http
import akka.stream.scaladsl.Source
import akka.util.Timeout
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.HttpsConnectionContext
import javax.net.ssl.SSLContext
import akka.http.scaladsl.ConnectionContext

trait ProxyRoute {

  private[this] val log = org.log4s.getLogger

  protected def proxyConfig: ProxyTarget

  protected def actorSystem: ActorSystem

  protected def sslContext: Option[SSLContext]

  def proxyRoute: Route = _proxyRoute

  def handle(requestPath: String): Route = {
    // use the timeout of the config
    //    implicit val timeoutDuration: FiniteDuration = proxyConfig.timeout.seconds
    //    implicit val timeout: Timeout = Timeout(timeoutDuration)

    implicit val _actorSystem = actorSystem
    implicit val materializer = ActorMaterializer()
    import scala.concurrent.ExecutionContext.Implicits.global

    val timeoutResponse = HttpResponse(
      StatusCodes.GatewayTimeout,
      entity = s"The proxy request did not responded after ${proxyConfig.timeout} seconds"
    )
    withRequestTimeout(proxyConfig.timeout.seconds, request => timeoutResponse) { ctx: RequestContext =>
      val request = ctx.request

      // Keep the query part of the original request
      val uri = Uri(
        if (requestPath.isEmpty) proxyConfig.uri
        else s"${proxyConfig.uri}/${requestPath}"
      ).copy(rawQueryString = request.uri.rawQueryString)

      val host = uri.authority.host.address()
      val port = uri.authority.port

      // keep headers, but not the host header
      val headers = request.headers.filter(header => header.isNot(Host.lowercaseName))
      // ++ Seq(Host(host))
      //      log.debug(s"headers for request [${headers}]")

      log.info(s"Received HttpRequest [${request}] at endpoint [${proxyConfig.path}] and path [${requestPath}] with query [${request.uri.queryString()}]")
      // outgoing connection uses ip and port from the configured uri
      log.info(s"About to request [$uri] with method [${request.method}] with entity [${request.entity}] and headers [${headers}]")

      // the final request to the target host
      val proxyReq = HttpRequest(method = request.method, uri = uri, entity = request.entity).withHeaders(headers)
      log.debug(s"Final http request [${proxyReq}]")
      //      log.debug(s"Flow is [${flow}]")

      val flow =
        if (proxyConfig.isHttps) {
          sslContext match {
            case Some(sslCtx) =>
              // Use explicit SSL config
              val httpsConCtx: HttpsConnectionContext = ConnectionContext.https(sslContext = sslCtx)
              Http().outgoingConnectionHttps(
                host = host,
                port = if (port > 0) port else 443,
                connectionContext = httpsConCtx,
                settings = ClientConnectionSettings(actorSystem)
              )
            case None =>
              // go with default HTTPS config
              Http().outgoingConnectionHttps(
                host = host,
                port = if (port > 0) port else 443,
                connectionContext = Http().defaultClientHttpsContext,
                settings = ClientConnectionSettings(actorSystem)
              )
          }
        } else {
          Http().outgoingConnection(
            host = host,
            port = if (port > 0) port else 80,
            settings = ClientConnectionSettings(actorSystem)
          )
        }

      val handler = Source.single(proxyReq).
        via(flow).
        runWith(Sink.head).
        flatMap { response =>
          response.status match {
            case e: StatusCodes.ServerError =>
              log.warn(s"503 Bad Gateway. The upstream (proxied) server returned with error: ${e}.")
              ctx.complete(HttpResponse(StatusCodes.BadGateway))
            case s =>
              log.debug(s"${s}. Received upstream response [$response]")
              ctx.complete(response)
          }
        }
      handler
    }

  }

  private[this] lazy val _proxyRoute: Route = {

    //    implicit val _actorSystem = actorSystem
    //    implicit val materializer = ActorMaterializer()
    //    import scala.concurrent.ExecutionContext.Implicits.global

    pathEnd {
      handle("")
    } ~
      path(Remaining) { requestPath =>
        handle(requestPath)
      }

    //    path(proxyConfig.path / Remaining) { requestPath => ctx =>
    //    path(Remaining) { requestPath => handle(requestPath) }
  }

}

