package blended.websocket.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Flow
import blended.security.login.api.{Token, TokenStore}
import blended.util.logging.Logger
import blended.websocket.WsUnitMessage

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class WebSocketProtocolHandler(system : ActorSystem, store : TokenStore) {

  private[this] val log = Logger[WebSocketProtocolHandler]
  private[this] implicit val eCtxt : ExecutionContext = system.dispatcher
  private[this] val cmdHandler : CommandHandlerManager = CommandHandlerManager.create(system)

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
      // We will pass the incoming Strings through the dispatcher
      // which will process them and generate WsUnitMessages as
      // appropriate. These will be converted to WebSocket messages
      // and sent back to the client
      .via(cmdHandler.newClient(info))
      .map {

        case result : WsUnitMessage =>
          TextMessage.Strict(result.encode())

        case o =>
          log.warn(s"cmd handler flow had unexpected result of type [${o.getClass().getName()}]")
          TextMessage.Strict(o.toString())
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
