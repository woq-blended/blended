package blended.websocket

import java.util.Base64

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import blended.security.login.api.Token
import blended.util.logging.Logger
import blended.websocket.internal.CommandHandlerManager.WsClientUpdate
import prickle._

import scala.util.{Failure, Success}

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
  def cmdPackage : WebSocketCommandPackage

  /**
    * The name of the handled command
    */
  def name : String

  /**
    * A short description of the handled command
    */
  def description : String

  private val log : Logger = Logger(getClass().getName())

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
    *
    * @param cmd The command to be executed
    * @param t The token with the security information
    * @return A [[WsContext]] indicating the success or failure of the command
    */
  final def handleCommand(cmd : WsMessageEncoded, t: Token)(implicit up : Unpickler[T]): WsContext = {

    val json : String = new String(Base64.getDecoder().decode(cmd.content))
    JsonHelper.decode[T](json) match {

      case Success(content) =>
        log.debug(s"Command content is [$content]")
        if (executeCommand.isDefinedAt(content)) {
          executeCommand(content)(t)
        } else {
          WsContext(cmdPackage.namespace, name, StatusCodes.NotImplemented.intValue, None)
        }

      case Failure(ex) =>
        WsContext(
          cmdPackage.namespace, name, StatusCodes.BadRequest.intValue,
          Some(s"Unable to decode command [${ex.getMessage()}] :")
        )
    }
  }

  def executeCommand : PartialFunction[T,  Token => WsContext]
}

object WsUpdateEmitter {

  private val log : Logger = Logger[WsUpdateEmitter.type]

  def emit[T](
    msg : T,
    token : Token,
    context: WsContext,
    pickler : Pickler[T]
  )(system : ActorSystem) : Unit = {

    log.debug(s"Emitting [$msg] to user [${token.user}]")
    val m : WsMessageEncoded = WsMessageEncoded.fromObject[T](
      context = context, t = msg
    )(pickler)

    system.eventStream.publish(WsClientUpdate(
      msg = m,
      token = token
    ))
  }
}

trait WebSocketCommandPackage {

  type T
  /**
    * The namespace the handle command belongs to
    */
  def namespace : String

  def commands : Seq[WebSocketCommandHandler[T]]

  def unpickler : Unpickler[T]

  private val log : Logger = Logger(getClass().getName())

  final def handleCommand(
    cmd : WsMessageEncoded,
    t : Token
  ): WsContext = {
    if (cmd.context.namespace != namespace) {
      WsContext(
        namespace = namespace,
        name = cmd.context.name,
        StatusCodes.BadRequest.intValue,
        Some(s"The given namespace [${cmd.context.namespace}] does not match [$namespace]")
      )
    } else {
      commands.find(_.name == cmd.context.name) match {
        case None =>
          val r : WsContext = WsContext(
            cmd.context.namespace, cmd.context.name,
            StatusCodes.NotFound.intValue,
            Some(s"The command [${cmd.context.namespace}:${cmd.context.name}] could not be found")
          )
          log.warn(r.toString)
          r
        case Some(c) => c.handleCommand(cmd, t)(unpickler)
      }
    }
  }
}
