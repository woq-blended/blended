package blended.websocket.internal

import blended.security.login.api.Token
import blended.util.logging.Logger
import blended.websocket.{ClientInfo, WebSocketCommandPackage, WsContext, WsMessageEncoded}

private[internal] case class CommandHandlerState(
  clients : Map[String, ClientInfo] = Map.empty,
  handler : Map[String, WebSocketCommandPackage] = Map.empty
) {

  private val log : Logger = Logger[CommandHandlerState]

  def addClient(info : ClientInfo) : CommandHandlerState = {
    log.info(s"Adding new WS client [${info.t.id}]")
    copy(
      clients = clients.view.filterKeys(_ != info.t.id).toMap ++ Map(info.t.id -> info)
    )
  }

  def removeClient(t : Token) : CommandHandlerState = {
    log.info(s"Removing WS client [${t.id}]")
    copy(
      clients = clients.view.filterKeys(_ != t.id).toMap
    )
  }

  def addHandler(h : WebSocketCommandPackage) : CommandHandlerState = {
    log.info(s"Adding command handler for namespace [${h.namespace}]")
    copy(handler = handler.view.filterKeys(_ != h.namespace).toMap ++ Map(h.namespace -> h))
  }

  def removeHandler(h : WebSocketCommandPackage) : CommandHandlerState = {
    log.info(s"Removing command package for namespace [${h.namespace}]")
    copy(handler = handler.view.filterKeys(_ != h.namespace).toMap)
  }

  def clientByToken(t : Token) : Option[ClientInfo] = clients.values.find(_.t.id == t.id)

  def packageByNS(ns : String) : Option[WebSocketCommandPackage] = handler.get(ns)

  def respondToClient(r : WsContext, t : Token) : Unit = clientByToken(t).foreach(_.clientActor ! WsMessageEncoded.fromContext(r))

}

