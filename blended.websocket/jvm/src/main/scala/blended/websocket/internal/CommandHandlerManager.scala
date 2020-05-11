package blended.websocket.internal

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import akka.http.scaladsl.model.StatusCodes
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{NotUsed, actor}
import blended.security.login.api.Token
import blended.util.logging.Logger
import blended.websocket._
import prickle.Unpickle

import scala.util.{Failure, Success}

trait CommandHandlerManager {
  /**
    * This method will be called every time a client has successfully authenticated.
    * The token will contain all the user info including the user id and the permissions
    * for the user. If required, the token will be passed to the command handlers so that
    * client specific permissions can be evaluated.
    * Essentially it will create a flow from Strings to WsUnitMessages. This flow will then
    * try to decode the incoming command and produce a Websockets message carrying a result.
    * @param t : The token with the client specific id and permissions.
    */
  def newClient(t : Token) : Flow[String, WsMessageEncoded, NotUsed]

  def addCommandPackage(pkg : WebSocketCommandPackage) : Unit
  def removeCommandPackage(pkg : WebSocketCommandPackage) : Unit
}

object CommandHandlerManager {

  case class AddCommandPackage(handler: WebSocketCommandPackage)
  case class RemoveCommandPackage(handler: WebSocketCommandPackage)
  case class NewClient(t : Token, clientActor : ActorRef)
  case class ClientClosed(t : Token)
  case class ReceivedMessage(t: Token, s : String)
  case class WsClientUpdate(
    msg : WsMessageEncoded,
    token : Token
  )

  /**
    * Create an empty command handler within an Actor system.
    */
  def create(system: ActorSystem): CommandHandlerManager = new CommandHandlerManager {
    // This creates one actor which will dispatch all incoming client
    // messages and dispatch them accordingly
    private val cmdHandler : ActorRef = system.actorOf(Props[CommandHandlerActor])

    // for each new client we will create a flow which will consume Strings and emit
    // WSMessageEnvelopes
    override def newClient(token : Token) : Flow[String, WsMessageEncoded, NotUsed] = {
      val in = Flow[String]
        .map(s => ReceivedMessage(token, s))
        .to(Sink.actorRef[ReceivedMessage](cmdHandler, ClientClosed(token)))

      // This materializes a new actor for the given client. All messages sent to this actor
      // will be sent to the client via Web Sockets
      // The new client will be registered with the DispatcherActor, which will then watch this
      // actor and dispatch events to the client as long as it is active.
      val out = Source.actorRef[WsMessageEncoded](1, OverflowStrategy.fail)
        .mapMaterializedValue { c => cmdHandler ! NewClient(token, c) }

      Flow.fromSinkAndSourceCoupled(in, out)
    }

    override def addCommandPackage(pkg: WebSocketCommandPackage): Unit = cmdHandler ! AddCommandPackage(pkg)

    override def removeCommandPackage(pkg: WebSocketCommandPackage): Unit = cmdHandler ! RemoveCommandPackage(pkg)
  }

  /**
    * The central Web sockets command handler.
    *
    * The central command handler will keep track of all registered specialized
    * command handlers, so that it can dispatch any inbound WsMessage to the
    * relevant command handler. It will also keep track of the connected clients,
    * as the command handlers will eventually send WsMessages to a particular client
    * via their emit method.
    */
  private class CommandHandlerActor extends Actor {

    private val log : Logger = Logger[CommandHandlerActor]

    // We start with no handlers and no clients
    override def preStart(): Unit = {
      context.become(handling(CommandHandlerState()))
      context.system.eventStream.subscribe(self, classOf[WsClientUpdate])
    }

    override def receive: Receive = Actor.emptyBehavior

    /**
      * Handle (de)registration of [[BlendedCommandPackage]]s.
      * @param state The CommandHandler State managing packages and clients.
      */
    private def handlePackages(state : CommandHandlerState) : Receive = {
      // Manage Ws Command Handler
      case AddCommandPackage(h) =>
        context.become(handling(state.addHandler(h)))
      case RemoveCommandPackage(h) =>
        context.become(handling(state.removeHandler(h)))
    }

    /**
      * Handle client (dis)connects.
      * @param state The CommandHandler State managing packages and clients.
      */
    private def handleClients(state : CommandHandlerState) : Receive = {
      // Manage client connects / disconnects
      case NewClient(info, clientActor) =>
        context.watch(clientActor)
        context.become(handling(state.addClient(ClientInfo(info, clientActor))))
      case ClientClosed(t) =>
        state.clients.get(t.id).foreach { ci =>
          ci.clientActor ! actor.Status.Success(())
        }
        context.become(handling(state.removeClient(t)))
      case Terminated(ca) =>
        state.clients.values.find(_.clientActor == ca).foreach { ci =>
          context.become(handling(state.removeClient(ci.t)))
        }
    }

    /**
      * Handle incoming commands.
      *
      * This is the main command dispatcher. It will try to decode an incoming
      * command into a [[WsContext]] and dispatch it to the corresponding
      * [[BlendedCommandPackage]] for further processing.
      *
      * Any implemented commands may use their emit method to dispatch messages
      * to a particular client.
      * @param state The CommandHandler State managing packages and clients.
      */
    private def handleCommand(state: CommandHandlerState) : Receive = {
      case rm : ReceivedMessage =>
        log.debug(s"Handling message [${rm.s}] for client [${rm.t.id}]")
        Unpickle[WsMessageEncoded].fromString(rm.s) match {

          case Success(msg) =>
            state.packageByNS(msg.context.namespace) match {
              case Some(p) =>
                val response : WsContext = p.handleCommand(msg, rm.t)
                state.respondToClient(response, rm.t)

              case None =>
                val result = WsContext(
                  namespace = msg.context.namespace,
                  name = msg.context.name,
                  status = StatusCodes.NotFound.intValue,
                  statusMsg = None
                )

                log.warn(result.toString())
                state.respondToClient(result, rm.t)
            }

          case Failure(_) =>

            val result = WsContext(
              namespace = "unknown",
              name = "unknown",
              status = StatusCodes.BadRequest.intValue,
              statusMsg = Some(s"Message [${rm.s}] not decoded into valid web socket request")
            )

            log.warn(result.toString())
            state.respondToClient(result, rm.t)
        }
    }

    /**
      * The actual receive method.
      *
      * Here we maintain the command handler state and processes incoming [[WsClientUpdate]]s.
      * A [[WsClientUpdate]] is normally sent over the Akka event stream by using a [[WebSocketCommandHandler]].
      * The Client Update will be dispatched to the client associated with the [[Token]] within
      * the update.
      *
      * @param state The CommandHandler State managing packages and clients.
      */
    private def handling(state : CommandHandlerState) : Receive =
      handlePackages(state)
        .orElse(handleClients(state))
        .orElse(handleCommand(state))
        .orElse {
          // Forward an emitted message from a command handler to the connected client
          case u : WsClientUpdate =>
            log.debug(s"Processing WsClientUpdate for [${u.token.id}]")
            state.clientByToken(u.token).foreach{ ci =>
              ci.clientActor ! u.msg
            }
        }
  }
}
