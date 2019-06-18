package blended.websocket.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Flow
import blended.jmx.{BlendedMBeanServerFacade, JmxObjectName}
import blended.security.login.api.{Token, TokenStore}
import blended.updater.config.ContainerInfo
import blended.updater.config.json.PrickleProtocol._
import blended.util.logging.Logger
import prickle._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class MgmtWebSocketServer(system : ActorSystem, store : TokenStore, facade : BlendedMBeanServerFacade) {

  private[this] val log = Logger[MgmtWebSocketServer]
  private[this] implicit val eCtxt : ExecutionContext = system.dispatcher
  private[this] val dispatcher = Dispatcher.create(system, facade)

  def route : Route = routeImpl

  private[this] lazy val routeImpl : Route = pathSingleSlash {
    log.info("Received Web Socket upgrade request")
    parameter("token") { token =>
      log.debug(s"Evaluating token [$token]")
      store.verifyToken(token) match {
        case Failure(e) =>
          log.error(s"Could not verify token [$token] : [${e.getMessage}]")
          complete(StatusCodes.Unauthorized)
        case Success(verified) =>
          log.info(s"Starting Web Socket message handler ... [${verified.id}]")

          store.getToken(verified.id) match {
            case None =>
              complete(StatusCodes.BadRequest)
            case Some(info) =>
              handleWebSocketMessages(dispatcherFlow(info))
          }
      }
    }
  }

  private[this] def dispatcherFlow(info : Token) : Flow[Message, Message, Any] = {
    Flow[Message]
      // We will only process TextMessage.Strict variants for now,
      // so we use a collect here
      .collect {
        case TextMessage.Strict(msg) => msg
      }
      // We will pass the incoming Strings through the dispatcher
      // which will process them and generate DispatcherEvents as
      // appropriate. These will be converted to WebSocket messages
      // and sent back to the client
      .via(dispatcher.newClient(info))
      .map {

        case ReceivedMessage(m) =>
          TextMessage.Strict(m)

        // NewData is a container to send arbitrary data back to the client
        case NewData(data) =>
          val msg : String = data match {
            case ctInfo : ContainerInfo => Pickle.intoString(ctInfo)
            case names : List[JmxObjectName] => Pickle.intoString(names)
            case msg : String => msg
            case _            => ""
          }
          log.debug(s"Sending message via Web Sockets : [$msg]")
          TextMessage.Strict(msg)

        case o =>
          TextMessage.Strict(o.toString())
      }
      .via(reportErrorsFlow)
  }

  def reportErrorsFlow[T] : Flow[T, T, Any] =
    Flow[T]
      .watchTermination()((_, f) => f.onComplete {
        case Failure(cause) =>
          println(s"WS stream failed with $cause")
        case _ => // ignore regular completion
      })
}
