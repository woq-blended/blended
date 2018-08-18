package blended.security.login.api

import java.security.PublicKey

import blended.security.BlendedPermissions

import scala.concurrent.duration.FiniteDuration

class TokenVerificationException(reason : String) extends Exception(reason)

trait TokenHandler {

  def createToken(user: String, expire: Option[FiniteDuration], permission: BlendedPermissions) : String

  def publicKey() : PublicKey

}
