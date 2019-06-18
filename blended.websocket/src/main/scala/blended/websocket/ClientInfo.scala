package blended.websocket

import blended.security.login.api.Token
import akka.actor.ActorRef

case class ClientInfo(
  id : String,
  token : Token,
  clientActor : ActorRef
)