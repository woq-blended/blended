package blended.websocket

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.TextMessage
import blended.security.login.api.Token
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
     *The package this command belongs to
    */
  def cmdPackage : WebSocketCommandPackage[T]

  /**
    * The name of the handled command
    */
  def name : String

  /**
    * A short description of the handled command
    */
  def description : String

  /**
    * Execute a command on behalf of a client. All permission information is contained
    * within the token. The result of the [[handleCommand]] method will simply indicate
    * whether the command was submitted successfully. (i.e. A command handler for the
    * command has to be registered). If the command was submitted successfully, eventually
    * the concrete implementation of the command handler will send one or more messages
    * to the client who has issued the command.
    *
    * For example, retrieving the current version of the WebSocket protocol is a one off
    * operation and results in a single [[String]]. On the other hand,
    * subscribing to container events or JMX events normally results in multiple WsMessages
    * with the event data as payload.
    * @param cmd The command to be executed
    * @param t The token with the security information
    * @return A [[WsResult]] indicating the success or failure of the command
    */
  final def handleCommand(cmd : WsMessageEncoded, t: Token)(implicit up : Unpickler[T]) : WsResult = {

    JsonHelper.decode[T](cmd.content) match {

      case Success(content) =>
        if (doHandleCommand.isDefinedAt(content)) {
          doHandleCommand(content)(t)
        } else {
          WsResult(cmdPackage.namespace, name, StatusCodes.NotImplemented.intValue, None)
        }

      case Failure(ex) =>
        WsResult(
          cmdPackage.namespace, name, StatusCodes.BadRequest.intValue,
          Some(s"Unable to decode command [${ex.getMessage()}] :")
        )
    }
  }

  def doHandleCommand : PartialFunction[T,  Token => WsResult]

  def emit(
    msg : T,
    token : Token,
    result: WsResult
  )(implicit p : Pickler[T]) : Try[Unit] = Try {
    val m : WsMessageEncoded = WsMessageEncoded(
      result = result, content = JsonHelper.encode(msg)
    )
    cmdPackage.handlerMgr ! WsClientUpdate(
      msg = TextMessage.Strict(Pickle.intoString(m)),
      token = token
    )
  }
}

trait WebSocketCommandPackage[T] {

  /**
    * The central actor managing all command handlers and clients.
    */
  def handlerMgr : ActorRef

  /**
    * The namespace the handle command belongs to
    */
  def namespace : String

  def commands : Seq[WebSocketCommandHandler[T]]

  def unpickler : Unpickler[T]

  final def handleCommand(
    cmd : WsMessageEncoded,
    t : Token
  ) : WsResult = {
    if (cmd.result.namespace != namespace) {
      WsResult(
        namespace = namespace,
        name = cmd.result.name,
        StatusCodes.BadRequest.intValue,
        Some(s"The given namespace [${cmd.result.namespace}] does not match [$namespace]")
      )
    } else {
      commands.find(_.name == cmd.result.name) match {
        case None =>
          WsResult(
            cmd.result.namespace, cmd.result.name,
            StatusCodes.NotFound.intValue,
            Some(s"The command [${cmd.result.namespace}:${cmd.result.name}] could not be found")
          )
        case Some(c) => c.handleCommand(cmd, t)(unpickler)
      }
    }
  }
}
