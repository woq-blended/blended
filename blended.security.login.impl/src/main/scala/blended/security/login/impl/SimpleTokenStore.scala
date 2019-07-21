package blended.security.login.impl

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import blended.security.BlendedPermissionManager
import blended.security.login.api.{AbstractTokenStore, Token, TokenHandler}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

object TokenStoreMessages {

  case class GetToken(id: String)
  case class RemoveToken(id: String)
  case class StoreToken(t :Token)
  case object ListTokens
  case object RemoveAllTokens
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

    case RemoveAllTokens => tokens.clear()
  }
}

class SimpleTokenStore(
  mgr: BlendedPermissionManager,
  tokenHandler: TokenHandler,
  system: ActorSystem
) extends AbstractTokenStore(mgr, tokenHandler) {

  import TokenStoreMessages._

  private[this] implicit val timeout : Timeout = Timeout(1.second)
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


  override def removeAllTokens(): Unit = storeActor ! RemoveAllTokens

  /**
    * @inheritdoc
    */
  override def storeToken(token: Token): Try[Token] = {
    Await.result((storeActor ? StoreToken(token)).mapTo[Try[Token]], 3.seconds)
  }

  override def listTokens(): Seq[Token] = {
    Await.result((storeActor ? ListTokens).mapTo[Seq[Token]], 3.seconds)
  }

  override def verifyToken(token: String): Try[Token] =  Token(token, publicKey())
}
