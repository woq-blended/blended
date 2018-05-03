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
import scala.concurrent.Future
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.model.headers.Location
import akka.stream.Materializer

trait ProxyRoute {

  private[this] val log = org.log4s.getLogger

  protected def proxyConfig: ProxyTarget

  protected def actorSystem: ActorSystem

  protected def sslContext: Option[SSLContext]

  def proxyRoute: Route = _proxyRoute

  private[this] type HttpClient = HttpRequest ⇒ Future[HttpResponse]

  private[this] lazy val _proxyRoute: Route = {

    //    implicit val _actorSystem = actorSystem
    //    implicit val materializer = ActorMaterializer()
    //    import scala.concurrent.ExecutionContext.Implicits.global

    pathEndOrSingleSlash {
      handle("")
    } ~
      path(Remaining) { requestPath =>
        handle(requestPath)
      }

    //    path(proxyConfig.path / Remaining) { requestPath => ctx =>
    //    path(Remaining) { requestPath => handle(requestPath) }
  }

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

      val simpleClient: HttpRequest => Future[HttpResponse] = sslContext match {
        case Some(sslCtx) =>
          // Use the explicit SSL Context for connections
          Http().singleRequest(_: HttpRequest, connectionContext = ConnectionContext.https(sslContext = sslCtx))
        case None =>
          // Use default SSL Context for connections
          Http().singleRequest(_: HttpRequest)
      }

      val request = ctx.request

      //      val uri = redirectTrace.headOption match {
      //        case Some(uri) =>
      //          // use the redirect uri
      //          uri
      //        case None =>
      //          // use the configured base-uri + the path
      //      }

      val uri = Uri(
        if (requestPath.isEmpty) proxyConfig.uri
        else s"${proxyConfig.uri}/${requestPath}"
      // Keep the query part of the original request
      ).copy(rawQueryString = request.uri.rawQueryString)

      //      val host = uri.authority.host.address()
      //      val port = uri.authority.port

      // keep headers, but not the host header
      val headers = request.headers.filter(header => header.isNot(Host.lowercaseName)) // ++ Seq(Host(host))
      //      log.debug(s"headers for request [${headers}]")

      log.info(s"Received HttpRequest [${request}] at endpoint [${proxyConfig.path}] and path [${requestPath}] with query [${request.uri.queryString()}]")

      // outgoing connection uses ip and port from the configured uri
      log.info(s"About to request [$uri] with method [${request.method}] with entity [${request.entity}] and headers [${headers}]")

      // the final request to the target host
      val proxyReq = HttpRequest(method = request.method, uri = uri, entity = request.entity).withHeaders(headers)
      log.debug(s"Final http request [${proxyReq}]")

      def handleResponse(request: HttpRequest, redirectCount: Int): Future[HttpResponse] = simpleClient(request).flatMap { response =>
        response.status match {

          case e: StatusCodes.ServerError =>
            // Always make sure you consume the response entity streams
            response.discardEntityBytes()
            // TODO: print response to log
            log.warn(s"503 Bad Gateway. The upstream (proxied) server returned with error: ${e}.")
            Future.successful(HttpResponse(StatusCodes.BadGateway))

          case r: StatusCodes.Redirection if redirectCount > 0 && Seq(301, 302, 307, 308).contains(r.intValue) =>

            val newUri = response.header[Location].get.uri
            val newRedirectCount = redirectCount - 1
            
            log.debug(s"${r}. Retry request with new URI [${newUri}] and redirectCount [${newRedirectCount}]")

            // Always make sure you consume the response entity streams
            response.discardEntityBytes()

            // create new request and retry
            handleResponse(request.withUri(newUri), newRedirectCount)

          case s =>
            log.debug(s"${s}. Received upstream response [$response]")
            Future.successful(response)
        }
      }

      ctx.complete(handleResponse(proxyReq, proxyConfig.redirectCount))

    }

  }

}

