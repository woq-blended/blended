package blended.websocket.internal

import blended.security.login.api.Token
import blended.util.logging.Logger
import blended.websocket.{ClientInfo, WebSocketCommandHandler}

private[internal] case class CommandHandlerState(
  clients : Map[String, ClientInfo] = Map.empty,
  handler : Map[String, WebSocketCommandHandler[_]] = Map.empty
) {

  private val log : Logger = Logger[CommandHandlerState]

  def addClient(info : ClientInfo) : CommandHandlerState = {
    log.info(s"Adding new WS client [${info.t.id}]")
    copy(
      clients = clients.filterKeys(_ != info.t.id) ++ Map(info.t.id -> info)
    )
  }

  def removeClient(t : Token) : CommandHandlerState = {
    log.info(s"Removing WS client [${t.id}]")
    copy(
      clients = clients.filterKeys(_ != t.id)
    )
  }

  def addHandler(h : WebSocketCommandHandler[_]) : CommandHandlerState = {
    log.info(s"Adding command handler for namespace [${h.namespace}]")
    copy(handler = handler.filterKeys(_ != h.namespace) ++ Map(h.namespace -> h))
  }

  def removeHandler(h : WebSocketCommandHandler[_]) : CommandHandlerState = {
    log.info(s"Removing command handler for namespace [${h.namespace}]")
    copy(handler = handler.filterKeys(_ != h.namespace))
  }
}

