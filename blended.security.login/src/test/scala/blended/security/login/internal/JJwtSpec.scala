package blended.security.login.internal

import blended.security.login.TokenHandler
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration._

class JJwtSpec extends FreeSpec with Matchers{

  private[this] val log = org.log4s.getLogger

  "The JJWT library should " - {

    "Allow to create an RSA based  JWT" in {

      val permissions = Array("profile-read", "profile-write")

      val th : TokenHandler = RSATokenHandler()
      val token = th.createToken("Andreas", Some(1.minute), permissions:_*)

      val clientClaims = th.verifyToken(token)

      log.info(clientClaims.getBody().getId())
      clientClaims.getHeader.getAlgorithm() should be ("RS512")
      clientClaims.getBody.getSubject() should be ("Andreas")
      clientClaims.getBody.get("permissions", classOf[String]) should be (permissions.mkString(","))
    }
  }


}
