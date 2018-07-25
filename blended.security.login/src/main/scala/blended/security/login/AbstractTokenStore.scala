package blended.security.login

import blended.security.BlendedPermissionManager
import com.sun.xml.internal.fastinfoset.util.CharArray
import javax.security.auth.callback._
import javax.security.auth.login.LoginContext

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
  override def newToken(user: String, password: Array[Char], ttl: Option[FiniteDuration] = None): Future[Try[Token]] = {

    val lc = new LoginContext("loginService", new StoreCallbackHandler(user, password))
    lc.login()

    val permissions = mgr.permissions(lc.getSubject())
    val token = Token(
      id = user,
      expiresAt = if (ttl.isDefined) System.currentTimeMillis() + ttl.map(_.toMillis).getOrElse(0l) else 0l,
      token = tokenHandler.createToken(user, ttl, permissions:_*)
    )
    storeToken(token)
  }

  private class StoreCallbackHandler(name: String, password: Array[Char]) extends CallbackHandler {

    override def handle(callbacks: Array[Callback]): Unit = {
      callbacks.foreach { cb: Callback =>
        cb match {
          case nameCallback: NameCallback => nameCallback.setName(name)
          case pwdCallback: PasswordCallback => pwdCallback.setPassword(password)
          case other => throw new UnsupportedCallbackException(other, "The submitted callback is not supported")
        }
      }
    }
  }
}
