package blended.security.login.api

import java.security.PublicKey

import blended.security.BlendedPermissions
import javax.security.auth.Subject

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class TokenVerificationException(reason : String) extends Exception(reason)

trait TokenHandler {

  def createToken(id : String, subj : Subject, expire : Option[FiniteDuration], permission : BlendedPermissions) : Try[Token]

  def publicKey() : PublicKey

}
