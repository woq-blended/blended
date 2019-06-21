package blended.websocket

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.TextMessage
import blended.websocket.internal.CommandHandlerManager.WsClientUpdate
import prickle._

import scala.util.{Failure, Success, Try}

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
    * The central actor managing all command handlers and clients.
    */
  def handlerMgr : ActorRef

  /**
    * The namespace of the commands handled by this handler
    */
  def namespace : String

  /**
    * Execute a command on behalf of a client. All permission information is contained
    * within the token. The result of the [[handleCommand]] method will simply indicate
    * whether the command was submitted successfully. (i.e. A command handler for the
    * command has to be registered). If the command was submitted successfully, eventually
    * the concrete implementation of the command handler will send one or more messages
    * to the client who has issued the command.
    *
    * For example, retrieving the current version of the WebSocket protocol is a one off
    * operation and results in a single [[WsStringMessage]]. On the other hand,
    * subscribing to container events or JMX events normally results in multiple WsMessages
    * with the event data as payload.
    * @param cmd The command to be executed
    * @param info The token with the security information
    * @return A [[WsUnitMessage]] indicating the success or failure of the command
    */
  final def handleCommand(cmd : WsMessageEncoded, info: ClientInfo)(implicit up : Unpickler[T]) : WsUnitMessage = {

    WsMessageEnvelope.decode[T](cmd.content) match {
      case Success((ctxt, content)) =>
        doHandleCommand(content) match {
          case Success(()) =>
            WsUnitMessage(ctxt.withStatus(StatusCodes.OK))
          case Failure(t) =>
            WsUnitMessage(ctxt.withStatus(StatusCodes.InternalServerError, t.getMessage()))
        }

      case Failure(t) => WsUnitMessage(
        cmd.context.withStatus(StatusCodes.BadRequest, s"Unable to decode command [${t.getMessage()}] :")
      )
    }

  }

  def doHandleCommand(cmd : T) : Try[Unit]

  def emit(
    msg : WsMessageEnvelope[T],
    status: Option[Int],
    statusMsg : Option[String],
    client : ClientInfo
  )(implicit p : Pickler[T]) : Try[Unit] = Try {
    val s : String = msg.encode(status, statusMsg)
    handlerMgr ! WsClientUpdate(
      msg = TextMessage.Strict(s),
      client = client
    )
  }
}
