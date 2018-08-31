package blended.security.login.api

import java.security.PublicKey

import blended.security.BlendedPermissions

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class TokenVerificationException(reason : String) extends Exception(reason)

trait TokenHandler {

  def createToken(id: String, expire: Option[FiniteDuration], permission: BlendedPermissions) : Try[Token]

  def publicKey() : PublicKey

}
