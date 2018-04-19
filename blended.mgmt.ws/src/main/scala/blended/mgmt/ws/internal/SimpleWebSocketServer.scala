package blended.mgmt.ws.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.http.scaladsl.server._
import Directives._

class SimpleWebSocketServer(implicit system: ActorSystem, materializer: ActorMaterializer) {

  val route : Route = path("echo") {
    handleWebSocketMessages(echoWebSocketService)
  }

  private[this] lazy val echoWebSocketService = Flow[Message].mapConcat {
    case tm : TextMessage =>
      TextMessage(tm.textStream) :: Nil
    case bm : BinaryMessage =>
      bm.getStreamedData.runWith(Sink.ignore, materializer)
      Nil
  }
}
