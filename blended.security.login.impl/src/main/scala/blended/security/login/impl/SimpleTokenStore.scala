package blended.security.login.impl

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import blended.security.json.PrickleProtocol._
import blended.security.login.api.{AbstractTokenStore, Token, TokenHandler}
import blended.security.{BlendedPermissionManager, BlendedPermissions}
import io.jsonwebtoken.Jwts
import prickle.Unpickle

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

object TokenStoreMessages {

  case class GetToken(id: String)
  case class RemoveToken(id: String)
  case class StoreToken(t :Token)
  case object ListTokens
}

class MemoryTokenStore extends Actor {

  import TokenStoreMessages._

  private[this] val tokens : mutable.Map[String, Token] = mutable.Map.empty

  override def receive: Receive = {
    case GetToken(id) =>
      sender() ! tokens.get(id)

    case RemoveToken(id) =>
      sender() ! tokens.remove(id)

    case StoreToken(t : Token) =>
      val r = sender()
      if (tokens.get(t.id).isDefined) {
        r ! Failure(new Exception(s"token with id [${t.id}] already exists."))
      } else {
        tokens += (t.id -> t)
        r ! Success(t)
      }

    case ListTokens => sender() ! tokens.values.toSeq
  }
}

class SimpleTokenStore(
  mgr: BlendedPermissionManager,
  tokenHandler: TokenHandler,
  system: ActorSystem
) extends AbstractTokenStore(mgr, tokenHandler) {

  import TokenStoreMessages._

  private[this] implicit val timeout : Timeout = Timeout(1.second)
  private[this] implicit val eCtxt : ExecutionContext = system.dispatcher
  private[this] val storeActor = system.actorOf(Props[MemoryTokenStore])
  /**
    * @inheritdoc
    */
  override def getToken(user: String): Option[Token] = {
    Await.result((storeActor ? GetToken(user)).mapTo[Option[Token]], 3.seconds)
  }

  /**
    * @inheritdoc
    */
  override def removeToken(user: String): Option[Token] = {
    Await.result((storeActor ? RemoveToken(user)).mapTo[Option[Token]], 3.seconds)
  }

  /**
    * @inheritdoc
    */
  override def storeToken(token: Token): Try[Token] = {
    Await.result((storeActor ? StoreToken(token)).mapTo[Try[Token]], 3.seconds)
  }

  override def listTokens(): Seq[Token] = {
    Await.result((storeActor ? ListTokens).mapTo[Seq[Token]], 3.seconds)
  }

  override def verifyToken(token: String): Try[Token] =  Try {

    val claims = Jwts.parser().setSigningKey(publicKey()).parseClaimsJws(token)
    val permissionsJson = claims.getBody().get("permissions", classOf[String])
    val permissions = Unpickle[BlendedPermissions].fromString(permissionsJson)

    Token(
      claims.getBody.getId,
      Option(claims.getBody.getExpiration).map(_.getTime).getOrElse(0),
      permissions.get,
      webToken = token
    )
  }
}
