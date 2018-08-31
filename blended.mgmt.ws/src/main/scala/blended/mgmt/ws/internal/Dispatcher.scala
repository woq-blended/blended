package blended.mgmt.ws.internal

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Status, Terminated}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import blended.security.GrantableObject
import blended.security.login.api.Token
import blended.updater.config.UpdateContainerInfo

sealed trait DispatcherEvent
case class NewClient(clientInfo: ClientInfo) extends DispatcherEvent
case class ClientClosed(info: Token) extends DispatcherEvent
case class ReceivedMessage(msg: String) extends DispatcherEvent
case class NewData(data: Any) extends DispatcherEvent

private[ws] case class ClientInfo(
  id : String,
  token: Token,
  clientActor : ActorRef
)

trait Dispatcher {

  def newClient(info: Token) : Flow[String, DispatcherEvent, Any]
}

object Dispatcher {
  def create(system: ActorSystem): Dispatcher = {
    val dispatcherActor = system.actorOf(Props[DispatcherActor])

    new Dispatcher {
      override def newClient(info: Token): Flow[String, DispatcherEvent, Any] = {

        val in = Flow[String]
          .map(s => ReceivedMessage(s))
          .to(Sink.actorRef[DispatcherEvent](dispatcherActor, ClientClosed(info)))

        // This actually creates a new client actor
        val out = Source.actorRef[DispatcherEvent](1, OverflowStrategy.fail)
          .mapMaterializedValue { c => dispatcherActor ! NewClient(ClientInfo(info.id, info, c)) }

        Flow.fromSinkAndSource(in, out)
      }
    }
  }

  class DispatcherActor extends Actor with ActorLogging {

    private[this] var clients: Map[String, ClientInfo] = Map.empty

    private[this] def dispatch(e: DispatcherEvent) : Unit = {

      // If we have to dispatch some data, we make sure, the client has the permission to see it
      val filteredClients = clients.values.filter { c =>
        e match {
          case NewData(obj) => obj match {
            case g : GrantableObject => c.token.permissions.allows(g.permission)
            case _ => true
          }
          case ReceivedMessage(msg) => true
          case _ => false
        }
      }

      log.info(s"Dispatching event [$e] to [${filteredClients.size}] clients")
      filteredClients.foreach(_.clientActor ! e)
    }


    override def preStart(): Unit = {
      context.system.eventStream.subscribe(self, classOf[UpdateContainerInfo])
    }

    override def postStop(): Unit = {
      context.system.eventStream.unsubscribe(self)
    }

    override def receive: Receive = {
      case m : ReceivedMessage =>
        dispatch(m)

      case UpdateContainerInfo(ctInfo) =>
        dispatch(NewData(ctInfo))

      case NewClient(info) =>
        log.info(s"New client connected [${info.id}]")
        context.watch(info.clientActor)
        clients += (info.id -> info)

      case ClientClosed(token) =>
        log.info(s"Client closed : ${token.id}")
        val entry = clients.get(token.id).foreach { info => info.clientActor ! Status.Success(Unit) }
        clients -= token.id

      case Terminated(client) =>
        clients = clients.filter { case (k, v) => v.clientActor != client }
    }
  }
}
