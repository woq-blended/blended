package blended.streams.transaction

import akka.actor.{Actor, ActorRef, Props}
import akka.util.Timeout
import blended.persistence.PersistenceService
import blended.streams.transaction.FlowTransactionActor.TransactionState
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object FlowTransactionActor {

  case class TransactionState(tid : String)

  def props(persistor: FlowTransactionPersistor, initialState: FlowTransaction) : Props =
    Props(new FlowTransactionActor(persistor, initialState))
}

class FlowTransactionActor(persistor: FlowTransactionPersistor, initialState: FlowTransaction) extends Actor {

  private [this] val log : Logger = Logger[FlowTransactionActor]

  override def receive: Receive = handleState(initialState)

  private[this] def updateState(state: FlowTransaction, evt: FlowTransactionEvent) : Try[FlowTransaction] = Try {
    val newState : FlowTransaction = state.updateTransaction(evt).get
    log.trace(s"New state is [$newState]")
    newState
  }

  private[this] def processEvent(requestor : ActorRef, state: FlowTransaction, event : FlowTransactionEvent): Unit = {
    updateState(state, event) match {
      case Success(s) =>
        persistor.persistTransaction(s)
        log.trace(s"Sending Transaction state [${s.id}] to [${requestor.path}]")
        requestor ! s
        context.become(handleState(s))
      case Failure(exception) =>
        log.warn(s"Failed to update transaction [${state.tid}] with event [$event] : ${exception.getMessage}")
    }
  }

  private[this] def handleState(state: FlowTransaction): Receive = {
    case e : FlowTransactionEvent =>
      processEvent(sender(), state, e)
    case TransactionState(_) =>
      sender() ! state
  }
}

object FlowTransactionManager {
  def props(pSvc : PersistenceService) : Props = Props(new FlowTransactionManager(pSvc))
}

class FlowTransactionManager(pSvc : PersistenceService) extends Actor {

  private[this] val log : Logger = Logger[FlowTransactionManager]

  private[this] implicit val timeout : Timeout = Timeout(3.seconds)
  //noinspection SpellCheckingInspection
  private[this] implicit val eCtxt : ExecutionContext = context.system.dispatcher
  private[this] val persistor : FlowTransactionPersistor = new FlowTransactionPersistor(pSvc)

  private val restoreTransaction : String => Option[ActorRef] = tid => persistor.restoreTransaction(tid) match {
    case Success(s) => Some(context.actorOf(FlowTransactionActor.props(persistor, s)))
    case Failure(_) => None
  }

  override def receive: Receive = handleEvent(Map.empty)

  private[this] def handleEvent(cache: Map[String, ActorRef]): Receive = {

    case e : FlowTransactionEvent =>
      val respondTo : ActorRef = sender()

      log.trace(s"Recording transaction event [$e]")

      cache.get(e.transactionId) match {
        case Some(a) =>
          a.tell(e, respondTo)

        case None =>
          log.debug(s"Trying to restore transaction actor for [${e.transactionId}]")

          restoreTransaction(e.transactionId) match {
            case Some(a) =>
              a.tell(e, respondTo)
              context.become(handleEvent(cache ++ Map(e.transactionId -> a)))

            case None =>
              log.debug(s"Creating new transaction actor for [${e.transactionId}]")
              val s : FlowTransaction = FlowTransaction(
                id = e.transactionId,
                creationProps = e.properties
              )
              val a : ActorRef = context.actorOf(FlowTransactionActor.props(persistor, s))
              a.tell(e, respondTo)
              context.become(handleEvent(cache ++ Map(e.transactionId -> a)))
          }
      }

    case state @ FlowTransactionActor.TransactionState(tid) =>
      val respondTo : ActorRef = sender()
      cache.get(tid) match {
        case Some(a) => a.tell(state, respondTo)
        case None => restoreTransaction(state.tid).foreach(_.tell(state, respondTo))
      }

    case m => log.warn(s"Unhandled msg [$m] ")
  }
}
