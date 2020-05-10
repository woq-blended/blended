package blended.security.login.rest.internal

import java.security.spec.X509EncodedKeySpec
import java.util.Base64

import scala.collection.immutable

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpHeader, HttpMethods, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import blended.security.BlendedPermissionManager
import blended.security.akka.http.JAASSecurityDirectives
import blended.security.login.api.TokenStore
import blended.util.logging.Logger
import javax.security.auth.Subject
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class LoginService(
  tokenstore : TokenStore,
  override val mgr : BlendedPermissionManager
)(implicit eCtxt : ExecutionContext) extends JAASSecurityDirectives {

  private[this] val log : Logger = Logger[LoginService]

  private[this] lazy val publicKeyPEM : String = {

    // scalastyle:off magic.number
    def lines(s : String)(current : List[String]) : List[String] = {
      if (s.length <= 64) {
        s :: current
      } else {
        lines(s.substring(64))(s.substring(0, 64) :: current)
      }
    }
    // scalastyle:on magic.number

    val key = tokenstore.publicKey()
    val encodedKey : Array[Byte] = new X509EncodedKeySpec(key.getEncoded()).getEncoded()
    val stringKey : String = Base64.getEncoder().encodeToString(encodedKey)

    val pemLines : List[String] = (
      "-----END PUBLIC KEY-----" ::
      lines(stringKey)(List("-----BEGIN PUBLIC KEY-----"))
    ).reverse

    pemLines.mkString("\n")
  }

  def route : Route = loginRoute ~ logoutRoute ~ publicKeyRoute

  private[this] val loginRoute = {

    // scalastyle:off magic.number
    val header : immutable.Seq[HttpHeader] = immutable.Seq(
      `Access-Control-Allow-Origin`.*,
      `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST, HttpMethods.OPTIONS),
      `Access-Control-Max-Age`(1000),
      `Access-Control-Allow-Headers`("origin", "x-csrftoken", "content-type", "accept", "authorization")
    )
    // scalastyle:on magic.number

    pathSingleSlash {
      options {
        complete(
          HttpResponse(StatusCodes.OK).withHeaders(header)
        )
      } ~
        get {
          log.warn("Login must be executed with a HTTP Post")
          complete(HttpResponse(StatusCodes.Forbidden).withHeaders(header))
        } ~
        post {
          // TODO: Make timeout for token expiry configurable
          authenticated { subj : Subject =>
            complete(tokenstore.newToken(subj, Some(1.minute)) match {
              case Failure(e) =>
                log.error(s"Could not create token : [${e.getMessage()}]")
                HttpResponse(StatusCodes.BadRequest).withHeaders(header)
              case Success(t) =>
                log.info(s"User [${t.user}] logged in successfully, token-id is [${t.id}]")
                HttpResponse(StatusCodes.OK, entity = t.webToken).withHeaders(header)
            })
          }
        }
    }
  }

  private[this] val logoutRoute = {
    path("logout") {
      complete(HttpResponse(StatusCodes.NotImplemented))
    }
  }

  private[this] val publicKeyRoute = {
    path("key") {
      get {
        complete(publicKeyPEM)
      }
    }
  }
}
