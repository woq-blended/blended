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
    implicit val timeoutDuration: Duration = proxyConfig.timeout.seconds
    implicit val timeout: Timeout = Timeout(proxyConfig.timeout.seconds)

    implicit val _actorSystem = actorSystem
    implicit val materializer = ActorMaterializer()
    import scala.concurrent.ExecutionContext.Implicits.global

    { ctx: RequestContext =>
      val request = ctx.request

      // Keep the query part of the original request
      val uri = Uri(
        if (requestPath.isEmpty) proxyConfig.uri
        else s"${proxyConfig.uri}/${requestPath}"
      ).copy(rawQueryString = request.uri.rawQueryString)

      log.info(s"Received HttpRequest [${request}] at endpoint [${proxyConfig.path}] and path [${requestPath}] with query [${request.uri.queryString()}]")
      // outgoing connection uses ip and port from the configured uri
      log.info(s"About to request [$uri] with method [${request.method}] with entity [${request.entity}] and headers [${request.headers}]")
      val flow =
        if (proxyConfig.isHttps) {
          sslContext match {
            case Some(sslCtx) =>
              // Use explicit SSL config
              val httpsConCtx: HttpsConnectionContext = ConnectionContext.https(sslContext = sslCtx)
              Http().outgoingConnectionHttps(
                host = uri.authority.host.address(),
                port = uri.authority.port,
                connectionContext = httpsConCtx,
                settings = ClientConnectionSettings(actorSystem).withConnectingTimeout(proxyConfig.timeout.seconds)
              )
            case None =>
              // go with default HTTPS config
              Http().outgoingConnectionHttps(
                host = uri.authority.host.address(),
                port = uri.authority.port,
                connectionContext = Http().defaultClientHttpsContext,
                settings = ClientConnectionSettings(actorSystem).withConnectingTimeout(proxyConfig.timeout.seconds)
              )
          }
        } else {
          Http().outgoingConnection(
            host = uri.authority.host.address(),
            port = uri.authority.port,
            settings = ClientConnectionSettings(actorSystem).withConnectingTimeout(proxyConfig.timeout.seconds)
          )
        }

      // keep headers, but not the host header
      val headers = request.headers.filter(header => header.isNot(Host.lowercaseName))

      // the final request to the target host
      val proxyReq = HttpRequest(method = request.method, uri = uri, entity = request.entity).withHeaders(headers)
      log.debug(s"Final http request [${proxyReq}]")
      log.debug(s"Flow is [${flow}]")

      val handler = Source.single(proxyReq).
        via(flow).
        runWith(Sink.head).
        flatMap { response =>
          response.status match {
            case e: StatusCodes.ServerError =>
              log.warn(s"The upstream server returned with error: ${e}")
              ctx.complete(HttpResponse(StatusCodes.BadGateway))
            case s =>
              log.debug(s"Reveived upstream response [$response]")
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

