package blended.mgmt.app

import akka.actor.{ActorRef, ActorSystem}
import blended.mgmt.app.backend.WSClientActor
import blended.mgmt.app.components.ContainerCollectionComponent
import blended.updater.config.ContainerInfo
import blended.updater.config.json.PrickleProtocol._
import com.github.ahnfelt.react4s._
import prickle._

case class Main() extends Component[NoEmit] {

  val system : ActorSystem = ActorSystem("MgmtApp")

  private[this] val ctListener = State[Option[ActorRef]](None)
  private[this] val container = State[Map[String, ContainerInfo]](Map.empty)

  // A web sockets handler decoding container Info's

  override def componentWillRender(get: Get): Unit =
    if (get(ctListener).isEmpty) {

      val handleCtInfo : PartialFunction[Any, Unit] = {
        case s : String =>
          Unpickle[ContainerInfo].fromString(s).map { ctInfo =>
            container.modify(old => old.filterKeys(_ != ctInfo.containerId) + (ctInfo.containerId -> ctInfo))
          }
      }

      ctListener.set(Some(system.actorOf(WSClientActor.props(
        "ws://localhost:9995/mgmtws/timer?name=test",
        handleCtInfo
      ))))
    }

  override def render(get: Get): Element = {
    E.div(Component(ContainerCollectionComponent, get(container)))
  }
}
