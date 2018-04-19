package blended.mgmt.app

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.github.ahnfelt.react4s._

object Tick {
  def apply() = new Tick()
}

case class Tick()

case class SampleComponent(system: P[ActorSystem]) extends Component[NoEmit] {

  private[this] val elapsed = State(0)

  private[this] var listener : Option[ActorRef] = None

  override def componentWillRender(get: Get): Unit = if (listener.isEmpty) {
    listener = Some {
      val actor = get(system).actorOf(Props(new Actor {
        override def receive: Receive = {
          case _ : Tick =>
            println("Received Tick")
            elapsed.modify(_ + 1)
        }
      }))

      get(system).eventStream.subscribe(actor, classOf[Tick])

      actor
    }
  }

  override def componentWillUnmount(get: Get): Unit = {
    listener.foreach{ l => get(system).eventStream.unsubscribe(l) }
  }

  override def render(get: Get): Element = {
    E.div(Text(s"${get(elapsed)} seconds already elapsed !"))
  }
}
