package blended.akka.http.proxy.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, Host, Location}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.http.scaladsl.{ConnectionContext, Http}
import blended.util.logging.Logger
import javax.net.ssl.SSLContext

import scala.concurrent.Future
import scala.concurrent.duration._

trait ProxyRoute {

  private[this] val log = Logger[ProxyRoute]

  protected def proxyConfig : ProxyTarget

  protected def actorSystem : ActorSystem

  protected def sslContext : Option[SSLContext]

  def proxyRoute : Route = _proxyRoute

  private[this] lazy val _proxyRoute : Route =
    pathEndOrSingleSlash {
      handle("")
    } ~
      path(Remaining) { requestPath =>
        handle(requestPath)
      }

  def handle(requestPath : String) : Route = {

    implicit val _actorSystem = actorSystem
    import scala.concurrent.ExecutionContext.Implicits.global

    val timeoutResponse = HttpResponse(
      StatusCodes.GatewayTimeout,
      entity = s"The proxy request did not responded after ${proxyConfig.timeout} seconds"
    )

    withRequestTimeout(proxyConfig.timeout.seconds, request => timeoutResponse) { ctx : RequestContext =>

      val simpleClient : HttpRequest => Future[HttpResponse] = sslContext match {
        case Some(sslCtx) =>
          // Use the explicit SSL Context for connections
          Http().singleRequest(_ : HttpRequest, connectionContext = ConnectionContext.https(sslContext = sslCtx))
        case None =>
          // Use default SSL Context for connections
          Http().singleRequest(_ : HttpRequest)
      }

      val authHeader : Seq[HttpHeader] =
        (proxyConfig.user, proxyConfig.password) match {
          case (Some(u), Some(p)) => Seq(
            Authorization(BasicHttpCredentials(u, p))
          )
          case (_, _) => Seq.empty
        }

      val request = ctx.request

      val uri = Uri(
        if (requestPath.isEmpty)  {
          proxyConfig.uri
        } else {
          s"${proxyConfig.uri}/${requestPath}"
        }
      // Keep the query part of the original request
      ).copy(rawQueryString = request.uri.rawQueryString)

      val headers = request.headers.filter{
        header =>
          // keep headers, but not the host header
          if (header.is(Host.lowercaseName)) {
            false
          // remove the authorization header if we have found user / pwd in the config
          } else if (header.is(Authorization.lowercaseName)){
            authHeader.isEmpty
          } else {
            true
          }
      } ++ authHeader

      log.info(s"Received HttpRequest [${request}] at endpoint [${proxyConfig.path}] and path [${requestPath}] with query [${request.uri.queryString()}]")

      log.info(s"About to request [$uri] with method [${request.method}] with entity [${request.entity}] and headers [${headers}]")

      // the final request to the target host
      val proxyReq = HttpRequest(method = request.method, uri = uri, entity = request.entity).withHeaders(headers)
      log.debug(s"Final http request [${proxyReq}]")

      def handleResponse(request : HttpRequest, redirectCount : Int, config : ProxyTarget) : Future[HttpResponse] = simpleClient(request).flatMap { response =>
        response.status match {

          case e : StatusCodes.ServerError =>
            //            response.discardEntityBytes()
            // consume entity (for log)
            val re = response.entity
            log.warn(s"503 Bad Gateway. The upstream (proxied) server returned with error [${e}] and response entity [${re}]")
            Future.successful(HttpResponse(StatusCodes.BadGateway))

          case r : StatusCodes.Redirection if redirectCount > 0 && Seq(301, 302, 307, 308).contains(r.intValue) =>

            val newUri = response.header[Location].get.uri
            val newRedirectCount = redirectCount - 1

            val newRequest : HttpRequest = config.redirectHeaderPolicy match {
              case RedirectHeaderPolicy.Client_Only =>
                request
              case RedirectHeaderPolicy.Redirect_Merge =>
                log.debug("Merging headers from redirect address into request header.")

                response.headers.foldLeft(request) { (r, h) =>
                  if (!r.getHeader(h.name()).isPresent) r.withHeaders(h) else r
                }
              case RedirectHeaderPolicy.Redirect_Replace =>
                log.debug("Replacing request headers with headers from redirect address")
                val oldHeaders = request.headers
                oldHeaders.foreach { h => request.removeHeader(h.name()) }
                request.withHeaders(response.headers)
            }

            log.info(s"${r}. Retry request with new URI [${newUri}], headers [${newRequest.headers}] and redirectCount [${newRedirectCount}]")

            // Always consume the response entity streams
            response.discardEntityBytes()

            // create new request and retry
            handleResponse(newRequest.withUri(newUri), newRedirectCount, config)

          case s =>
            log.debug(s"${s}. Received upstream response with status [${response.status}] and headers [${response.headers}]")
            Future.successful(response)
        }
      }

      ctx.complete(handleResponse(proxyReq, proxyConfig.redirectCount, proxyConfig))
    }
  }
}

