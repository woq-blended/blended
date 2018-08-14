package blended.security.login

import java.security.PublicKey

import blended.security.{BlendedPermissionManager, PasswordCallbackHandler}
import javax.security.auth.login.{LoginContext, LoginException}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

abstract class AbstractTokenStore(
  mgr : BlendedPermissionManager,
  tokenHandler: TokenHandler
) extends TokenStore {
  /**
    * @inheritdoc
    */
  @throws[LoginException]
  override def newToken(user: String, password: Array[Char], ttl: Option[FiniteDuration] = None): Future[Try[Token]] = {

    val lc = new LoginContext("loginService", new PasswordCallbackHandler(user, password))
    lc.login()

    val token = Token(
      id = user,
      expiresAt = if (ttl.isDefined) System.currentTimeMillis() + ttl.map(_.toMillis).getOrElse(0l) else 0l,
      webToken = tokenHandler.createToken(user, ttl, mgr.permissions(lc.getSubject()))
    )
    storeToken(token)
  }

  override def publicKey(): PublicKey = tokenHandler.publicKey()
}
