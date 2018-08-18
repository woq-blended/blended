package blended.security.login.api

import java.security.PublicKey

import blended.security.BlendedPermissionManager
import blended.security.boot.UserPrincipal
import javax.security.auth.Subject
import javax.security.auth.login.LoginException

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Try}

abstract class AbstractTokenStore(
  mgr : BlendedPermissionManager,
  tokenHandler: TokenHandler
) extends TokenStore {
  /**
    * @inheritdoc
    */
  @throws[LoginException]
  override def newToken(subj: Subject, ttl: Option[FiniteDuration] = None)(implicit eCtxt : ExecutionContext) : Future[Try[Token]] = {

    subj.getPrincipals(classOf[UserPrincipal]).asScala.toSeq match {
      case Seq() => Future(Failure(new LoginException("Authenticated subject is missing the user principal")))
      case h +: _ =>
        val user = h.getName()
        val token = Token(
          id = user,
          expiresAt = if (ttl.isDefined) System.currentTimeMillis() + ttl.map(_.toMillis).getOrElse(0l) else 0l,
          webToken = tokenHandler.createToken(user, ttl, mgr.permissions(subj))
        )

        storeToken(token)
    }
  }

  override def publicKey(): PublicKey = tokenHandler.publicKey()
}
