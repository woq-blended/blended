package blended.mgmt.ws.internal

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Status, Terminated}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

sealed trait DispatcherEvent
case class NewClient(id: String, client: ActorRef) extends DispatcherEvent
case class ClientClosed(id: String) extends DispatcherEvent
case class ReceivedMessage(msg: String) extends DispatcherEvent
case class TimerEvent(e: Timer) extends DispatcherEvent

case class Timer(time: Long)

trait Dispatcher {
  def newClient(name: String) : Flow[String, DispatcherEvent, Any]
  def injectEvent(event: DispatcherEvent) : Unit
}

object Dispatcher {
  def create(system: ActorSystem): Dispatcher = {
    val dispatcherActor = system.actorOf(Props[DispatcherActor])

    new Dispatcher {
      override def newClient(name: String): Flow[String, DispatcherEvent, Any] = {

        val in = Flow[String]
          .map(ReceivedMessage(_))
          .to(Sink.actorRef[DispatcherEvent](dispatcherActor, ClientClosed(name)))

        val out = Source.actorRef[DispatcherEvent](1, OverflowStrategy.fail)
          .mapMaterializedValue { c => dispatcherActor ! NewClient(name, c) }

        Flow.fromSinkAndSource(in, out)
      }

      override def injectEvent(event: DispatcherEvent): Unit = dispatcherActor ! event
    }
  }

  class DispatcherActor extends Actor with ActorLogging {

    private[this] var clients: Map[String, ActorRef] = Map.empty

    override def preStart(): Unit = context.system.eventStream.subscribe(self, classOf[Timer])

    override def postStop(): Unit = context.system.eventStream.unsubscribe(self)

    override def receive: Receive = {
      case t : Timer =>
        log.info(s"Dispatching timer message to [${clients.size}] clients ")
        clients.values.foreach(_ ! TimerEvent(t))

      case NewClient(id, client) =>
        log.info(s"New client connected [$id]")
        context.watch(client)
        clients += (id -> client)
        client ! ReceivedMessage("Accepted")

      case ClientClosed(c) =>
        log.info(s"Client closed : $c")
        val entry = clients.get(c).get
        entry ! Status.Success(Unit)
        clients -= c

      case Terminated(client) =>
        clients = clients.filter { case (k, v) => v != client }
    }
  }


}