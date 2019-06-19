package blended.ws.jmx.internal

import blended.security.login.api.Token
import blended.websocket.{WebsocketCommandHandler, WsData}

import scala.util.Try

class JmxQueryCommandHandler extends WebsocketCommandHandler {
  /**
    * Execute a command on behalf of a client. All permission information is contained
    * within the token
    *
    * @param cmd  The command to be executed
    * @param info The token with the security information
    * @return A WsData encoded response as a [[Try]]
    */
  override def handleCommand(cmd: WsData, info: Token): Try[WsData] = ???
}
