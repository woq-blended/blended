package blended.websocket.internal

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Status, Terminated}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import blended.security.login.api.Token

sealed trait DispatcherEvent
case class NewClient(token : Token, clientActor : ActorRef) extends DispatcherEvent
case class ClientClosed(info : Token) extends DispatcherEvent
case class ReceivedMessage(msg : String) extends DispatcherEvent
case class NewData(data : Any) extends DispatcherEvent

trait Dispatcher {
  def newClient(info : Token) : Flow[String, DispatcherEvent, Any]
}

object Dispatcher {
  def create(system : ActorSystem) : Dispatcher = {
    val dispatcherActor = system.actorOf(Props[DispatcherActor])

    info : Token => {
      val in = Flow[String]
        .map(s => ReceivedMessage(s))
        .to(Sink.actorRef[DispatcherEvent](dispatcherActor, ClientClosed(info)))

      // This materializes a new actor for the given client. All messages sent to this actor
      // will be sent to the client via Web Sockets
      // The new client will be registered with the DispatcherActor, which will then watch this
      // actor and dispatch events to the client as long as it is active.
      val out = Source.actorRef[DispatcherEvent](1, OverflowStrategy.fail)
        .mapMaterializedValue { c => dispatcherActor ! NewClient(info, c) }

      Flow.fromSinkAndSourceCoupled(in, out)
    }
  }

  // The dispatcher actor handles any incoming commands and passes the responses back to the
  // client
  class DispatcherActor extends Actor with ActorLogging {

    override def receive : Receive = Actor.emptyBehavior

    // We start with no clients connected
    override def preStart() : Unit = {
      context.become(dispatching(Map.empty))
    }

//    private def dispatch(e : DispatcherEvent)(clients : Map[String, Token]) : Unit = {
//
//      // If we have to dispatch some data, we make sure, the client has the permission to see it
//      val filteredClients = clients.values.filter { t =>
//        e match {
//          case NewData(obj) => obj match {
//            case g : GrantableObject => t.permissions.allows(g.permission)
//            case _                   => true
//          }
//          case ReceivedMessage(_) => true
//          case _                  => false
//        }
//      }
//
//      log.debug(s"Dispatching event [$e] to [${filteredClients.map(_.id)}] [${filteredClients.size}/${clients.size}]")
//
//      // After we have filtered the clients we send the resulting event to the outbound leg of each client
//      // matching the permission filter
//      filteredClients.foreach(_.clientActor ! e)
//    }

    private def dispatching(clients : Map[String, (Token, ActorRef)]) : Receive = {
//      case m : ReceivedMessage =>
//        dispatch(m)(clients)
//
//      case nd : NewData =>
//        log.debug(s"Received data to dispatch [${nd.data.getClass().getName()}][${nd.data}]")
//        dispatch(nd)(clients)

      case NewClient(info, clientActor) =>
        log.info(s"New client connected [${info.id}]")
        context.watch(clientActor)
        context.become(dispatching(clients ++ Map(info.id -> (info, clientActor))))

      case ClientClosed(token) =>
        log.info(s"Client closed : ${token.id}")
        clients.get(token.id).foreach { case (_, clientActor) => clientActor ! Status.Success(Unit) }
        context.become(dispatching(clients.filterKeys(_ != token.id)))

      case Terminated(client) =>
        context.become(dispatching(clients.filter { case (_, (_, a)) => a != client }))
    }
  }
}
