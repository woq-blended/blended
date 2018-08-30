package blended.mgmt.ws.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Flow
import blended.security.login.api.{TokenInfo, TokenStore}
import blended.updater.config.ContainerInfo
import blended.updater.config.json.PrickleProtocol._
import blended.util.logging.Logger
import prickle.Pickle

import scala.util.{Failure, Success}

class SimpleWebSocketServer(system: ActorSystem, store: TokenStore) {

  private[this] val log = Logger[SimpleWebSocketServer]
  private[this] implicit val eCtxt = system.dispatcher
  private[this] val dispatcher = Dispatcher.create(system)

  def route : Route = routeImpl

  private[this] lazy val routeImpl : Route = pathSingleSlash {
    parameter("token") {token =>
        store.verifyToken(token) match {
          case Failure(e) =>
            log.error(s"Could not verify token [$token] : [${e.getMessage}]")
            complete(StatusCodes.BadRequest)
          case Success(info) =>
            log.info(s"Starting Web Socket message handler ... [${info.id}]")
            handleWebSocketMessages(dispatcherFlow(info))
        }
    }
  }

  private[this] def dispatcherFlow(info: TokenInfo) : Flow[Message, Message, Any] = {
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) => msg
      }
      .via(dispatcher.newClient(info))
      .map {

        case ReceivedMessage(m) =>
          TextMessage.Strict(m)

        case NewData(data) => data match {
          case ctInfo : ContainerInfo =>
            val json : String = Pickle.intoString(ctInfo)
            TextMessage.Strict(json)

          case _ => TextMessage.Strict("")
        }

        case o =>
          TextMessage.Strict(o.toString())
      }
      .via(reportErrorsFlow)
  }

  def reportErrorsFlow[T]: Flow[T, T, Any] =
    Flow[T]
      .watchTermination()((_, f) => f.onComplete {
        case Failure(cause) =>
          println(s"WS stream failed with $cause")
        case _ => // ignore regular completion
      })
}
