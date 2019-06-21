package blended.websocket

import akka.actor.ActorRef
import blended.security.login.api.Token

case class ClientInfo(t : Token, clientActor: ActorRef)
