package blended.security.login.rest.internal

import java.security.spec.X509EncodedKeySpec

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import blended.security.akka.http.JAASSecurityDirectives
import blended.security.login.api.TokenStore
import javax.security.auth.Subject
import org.slf4j.LoggerFactory
import sun.misc.BASE64Encoder

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class LoginService(tokenstore: TokenStore)(implicit eCtxt: ExecutionContext) extends JAASSecurityDirectives {

  private[this] val log = LoggerFactory.getLogger(classOf[LoginService])

  def route = httpRoute

  private[this] lazy val httpRoute = loginRoute ~ logoutRoute ~ publicKeyRoute

  private[this] val loginRoute = {
    pathSingleSlash {
      get {
        log.warn("Login must be executed with a HTTP Post")
        complete(HttpResponse(StatusCodes.Forbidden))
      } ~
      post {
        authenticated { subj : Subject =>
          val t : Future[HttpResponse] = tokenstore.newToken(subj, Some(1.minute)).map {
            case Failure(e) => HttpResponse(StatusCodes.BadRequest)
            case Success(t) => HttpResponse(StatusCodes.OK, entity = t.webToken)
          }

          complete(t)
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
        val key = tokenstore.publicKey()

        val encodedKey : Array[Byte] = new X509EncodedKeySpec(key.getEncoded()).getEncoded()
        val encoder = new BASE64Encoder()

        complete(HttpResponse(StatusCodes.OK, entity = encoder.encode(encodedKey)))
      }
    }
  }
}
