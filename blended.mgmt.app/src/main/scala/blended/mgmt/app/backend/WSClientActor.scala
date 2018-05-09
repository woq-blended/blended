package blended.mgmt.app.backend

import akka.actor.{Actor, ActorLogging, Props}
import org.scalajs.dom.raw.WebSocket

object WSClientActor {

  def props(url: String, onMessage: PartialFunction[Any, Unit]) : Props = {
    Props(new WSClientActor(url, onMessage))
  }
}

class WSClientActor(url: String, onMessage: PartialFunction[Any, Unit]) extends Actor with ActorLogging {

  private[this] var webSocket : Option[WebSocket] = None

  case object Initialize
  case class Closed(reason: String)

  override def preStart(): Unit = self ! Initialize

  override def receive: Receive = {
    case Initialize =>
      if (webSocket.isEmpty) {
        val socket = new WebSocket(url)

        socket.onopen = {_ =>  log.info(s"Connected to [$url].") }

        socket.onclose = { e =>
          self ! Closed(e.reason)
        }

        socket.onerror = { e =>
          webSocket.foreach(_.close())
          self ! Closed("Closed upon error in Web Socket!")
          webSocket = None
        }
        socket.onmessage = { e =>
          onMessage(e.data)
        }

        webSocket = Some(socket)
      }
    case Closed(reason) =>
      log.info(s"Web Socket connection to [$url] closed: [$reason]")
      webSocket = None
  }
}