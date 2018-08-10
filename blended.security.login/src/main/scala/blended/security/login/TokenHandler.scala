package blended.security.login

import java.security.PublicKey

import scala.concurrent.duration.FiniteDuration

class TokenVerificationException(reason : String) extends Exception(reason)

trait TokenHandler {

  def createToken(user: String, expire: Option[FiniteDuration], permission: String*) : String

  def publicKey() : PublicKey

}
