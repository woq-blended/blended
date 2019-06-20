package blended.websocket

import blended.security.login.api.Token

import scala.util.Try

/**
  * A web socket command handler.
  *
  * A connected web sockets client will have authenticated itself and will have a [[Token]]
  * describing the permissions for that particular user. A command handler will receive messages
  * in the form of WsData objects and will have to decode the command and act accordingly.
  * It will respond with another WsData object that will carry an encoded response for the
  * client that has made the request.
  *
  * A command handler is built over an arbitrary type T, which is the implementation of the
  * command payload and response.
  */
trait WebSocketCommandHandler[T] {

  /**
    * Execute a command on behalf of a client. All permission information is contained
    * within the token
    * @param cmd The command to be executed
    * @param info The token with the security information
    * @return A WsData encoded response as a [[Try]]
    */
  def handleCommand(cmd : WsCommandEnvelope[T], info: Token) : Try[WsCommandEnvelope[T]]
}
