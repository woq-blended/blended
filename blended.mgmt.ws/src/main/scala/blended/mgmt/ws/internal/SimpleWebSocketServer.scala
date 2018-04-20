package blended.mgmt.ws.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.duration._
import akka.http.scaladsl.server._
import Directives._

import scala.util.Failure

class SimpleWebSocketServer(implicit system: ActorSystem, materializer: ActorMaterializer) {

  private[this] val log = org.log4s.getLogger
  private[this] implicit val eCtxt = system.dispatcher

  val dispatcher = Dispatcher.create(system)

  system.scheduler.schedule(1.second, 1.second) {
    system.eventStream.publish(Timer(System.currentTimeMillis()))
  }

  val route : Route = path("timer") {
    parameter('name) {
      log.info("Starting Web Socket message handler ... name")
      name => handleWebSocketMessages(dispatcherFlow(name))
    }
  }

  private[this] def dispatcherFlow(name: String) : Flow[Message, Message, Any] = {
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) => msg
      }
      .via(dispatcher.newClient(name))
      .map {
        case m => TextMessage.Strict(m.toString)
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
