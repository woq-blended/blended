package blended.security.login

import io.jsonwebtoken.{Claims, Jws}

import scala.concurrent.duration.FiniteDuration

class TokenVerificationException(reason : String) extends Exception(reason)

trait TokenHandler {

  def createToken(user: String, expire: Option[FiniteDuration], permission: String*) : String

  def verifyToken(token : String) : Jws[Claims]

}
