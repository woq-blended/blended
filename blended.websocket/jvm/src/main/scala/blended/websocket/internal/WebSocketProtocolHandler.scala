package blended.websocket.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Flow
import blended.security.login.api.{Token, TokenStore}
import blended.util.logging.Logger
import blended.websocket.{WebSocketCommandPackage, WsContext, WsMessageEncoded}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class WebSocketProtocolHandler(system : ActorSystem, store : TokenStore) {

  private[this] val log = Logger[WebSocketProtocolHandler]
  private[this] implicit val eCtxt : ExecutionContext = system.dispatcher
  private[this] val cmdHandler : CommandHandlerManager = CommandHandlerManager.create(system)

  def addCommandPackage(pkg : WebSocketCommandPackage) : Unit = cmdHandler.addCommandPackage(pkg)
  def removeCommandPackage(pkg : WebSocketCommandPackage) : Unit = cmdHandler.removeCommandPackage(pkg)

  def route : Route = routeImpl

  private[this] lazy val routeImpl : Route = pathSingleSlash {
    log.debug("Received Web Socket upgrade request")
    parameter("token") { token =>
      log.debug(s"Evaluating token [$token]")
      store.verifyToken(token) match {
        case Failure(e) =>
          log.error(s"Could not verify token [$token] : [${e.getMessage}]")
          complete(StatusCodes.Unauthorized)
        case Success(verified) =>
          log.info(s"Starting Web Socket message handler for token [${verified.id}]")

          store.getToken(verified.id) match {
            case None =>
              complete(StatusCodes.BadRequest)
            case Some(info) =>
              // A web socket handler is simply a flow Message -> Message
              // Inbound messages will be treated as commands coming from the
              // client, outbound message are usually WsEncoded messages that
              // can carry a status code and a JSON encoded payload
              handleWebSocketMessages(handlerFlow(info))
          }
      }
    }
  }

  private[this] def handlerFlow(info : Token) : Flow[Message, Message, Any] = {
    Flow[Message]
      // We will only process TextMessage.Strict variants for now,
      // so we use a collect here
      .collect {
        case TextMessage.Strict(msg) => msg
      }
      // Incoming Strings are always treated as commands and processed
      // by the command handler chain. The result for each and every
      // command is a WsResult, which is passed back to the client here.
      // A command *might* send additional messages to the client as a
      // result of executing the command (i.e. when setting up a listener
      // on Container events).
      // The additional messages are not passed through, but are sent directly
      // to the client as websocket messages
      .via(cmdHandler.newClient(info))
      .collect {
        case result : WsContext =>
          log.debug(s"Result of web socket command is [$result]")
          val msg = WsMessageEncoded.fromContext(result)
          log.debug(s"Raw response is [$msg]")
          TextMessage.Strict(msg)
      }
      .via(reportErrorsFlow)
  }

  def reportErrorsFlow[T] : Flow[T, T, Any] =
    Flow[T]
      .watchTermination()((_, f) => f.onComplete {
        case Failure(cause) =>
          log.error(cause)(s"WS stream failed : [${cause.getMessage()}]")
        case _ => // ignore regular completion
      })
}
