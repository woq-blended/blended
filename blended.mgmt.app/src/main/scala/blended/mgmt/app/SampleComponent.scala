package blended.mgmt.app

import akka.actor.ActorRef
import com.github.ahnfelt.react4s._
import org.scalajs.dom.WebSocket

object Tick {
  def apply() = new Tick()
}

case class Tick()

case class SampleComponent() extends Component[NoEmit] {

  private[this] val webSocket = State[Option[WebSocket]](None)
  private[this] val log = State[List[String]](List.empty)

  private[this] var listener : Option[ActorRef] = None

  override def componentWillRender(get: Get): Unit =
    if (get(webSocket).isEmpty) {
      val socket = new WebSocket("ws://localhost:9995/mgmtws/timer?name=test")
      log.modify(_ :+ "Connecting to: " + socket.url)
      socket.onopen = {_ => log.modify(_ :+ "Connected." )}
      socket.onclose = {e => log.modify(_ :+ "Closed: reason = " + e.reason)}
      socket.onerror = {e => log.modify(_ :+ "Error: " + e.toString())}
      socket.onmessage = {e => log.modify(_ :+ "Received: " + e.data)}

      webSocket.set(Some(socket))
    }

  override def render(get: Get): Element = {
    E.div(Tags(
      get(log).map(entry => E.div(Text(entry)))
    ))
  }
}
