package blended.security.login.rest.internal

import java.security.spec.X509EncodedKeySpec

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpHeader, HttpMethods, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import blended.security.BlendedPermissionManager
import blended.security.akka.http.JAASSecurityDirectives
import blended.security.login.api.TokenStore
import blended.util.logging.Logger
import javax.security.auth.Subject
import sun.misc.BASE64Encoder

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class LoginService(
  tokenstore: TokenStore,
  override val mgr : BlendedPermissionManager
)(implicit eCtxt: ExecutionContext) extends JAASSecurityDirectives {

  private[this] val log : Logger = Logger[LoginService]

  private[this] lazy val publicKeyPEM : String = {

    def lines(s : String)(current : List[String] = List.empty) : List[String] = {
      if (s.length <= 64) {
        s :: current
      } else {
        lines(s.substring(64))( s.substring(0,64) :: current)
      }
    }

    val key = tokenstore.publicKey()
    val encodedKey : Array[Byte] = new X509EncodedKeySpec(key.getEncoded()).getEncoded()

    val pemLines : List[String] = (
      "-----END PUBLIC KEY-----" ::
      lines(new BASE64Encoder().encode(encodedKey))(List("-----BEGIN PUBLIC KEY-----"))
    ).reverse

    pemLines.mkString("\n")
  }

  def route: Route = loginRoute ~ logoutRoute ~ publicKeyRoute

  private[this] val loginRoute = {

    val header : Seq[HttpHeader] = Seq(
      `Access-Control-Allow-Origin`.*,
      `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST, HttpMethods.OPTIONS),
      `Access-Control-Max-Age`(1000),
      `Access-Control-Allow-Headers`("origin", "x-csrftoken", "content-type", "accept", "authorization")
    )

    pathSingleSlash {
      options {
        complete(
          HttpResponse(StatusCodes.OK).withHeaders(header:_*)
        )
      } ~
      get {
        log.warn("Login must be executed with a HTTP Post")
        complete(HttpResponse(StatusCodes.Forbidden).withHeaders(header:_*))
      } ~
      post {
        // TODO: Make timeout for token expiry configurable
        authenticated { subj : Subject =>
          complete(tokenstore.newToken(subj, Some(1.minute)) match {
            case Failure(e) =>
              log.error(s"Could not create token : [${e.getMessage()}]")
              HttpResponse(StatusCodes.BadRequest).withHeaders(header:_*)
            case Success(t) =>
              log.info(s"User [${t.user}] logged in successgully, token-id is [${t.id}]")
              HttpResponse(StatusCodes.OK, entity = t.webToken).withHeaders(header:_*)
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
