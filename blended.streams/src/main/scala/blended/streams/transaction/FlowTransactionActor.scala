package blended.streams.transaction

import akka.Done
import akka.actor.{Actor, ActorRef, Props}
import akka.persistence.SnapshotOffer
import akka.util.Timeout
import blended.streams.persistence.RestartableActor
import blended.streams.persistence.RestartableActor.RestartActor
import blended.streams.transaction.FlowTransactionActor.State
import blended.streams.transaction.FlowTransactionManager.RestartTransactionActor
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object FlowTransactionActor {

  case object TransactionProcessingComplete
  case class State(tid : String)

  def props(initialState: FlowTransaction) : Props =
    Props(new FlowTransactionActor(initialState))
}

class FlowTransactionActor(initialState: FlowTransaction) extends RestartableActor {

  private val log = Logger[FlowTransactionActor]
  private var state : FlowTransaction = initialState

  override def persistenceId: String = initialState.tid

  def updateState(evt: FlowTransactionEvent) : Try[FlowTransaction] = Try {
    state = state.updateTransaction(evt).get
    log.trace(s"New state is [$state]")
    state
  }

  val transactionRecover : Receive = {
    case evt : FlowTransactionEvent =>
      log.trace(s"Replaying FlowTransactionEvent [$evt]")
      updateState(evt)
    case SnapshotOffer(_, snapshot : FlowTransaction) => state = snapshot
  }

  override def receiveRecover: Receive = transactionRecover

  private[this] def processEvent(requestor : ActorRef, event : FlowTransactionEvent) = {
    updateState(event) match {
      case Success(s) =>
        log.trace(s"Sending Transaction state to [${requestor.path}]")
        requestor ! s
        persist(event) { e => } // do nothing
      case Failure(exception) =>
        log.warn(s"Failed to update transaction [${state.tid}] with event [$event]")
    }
  }

  private val cmdReceive : Receive = {
    case e : FlowTransactionEvent =>
      processEvent(sender(), e)
    case State(_) =>
      sender() ! state
  }

  override def receiveCommand: Receive = cmdReceive.orElse(restartReceive)
}

object FlowTransactionManager {

  // TODO : Review this is only required for testing
  case class RestartTransactionActor(id: String)

  def props() : Props = Props(new FlowTransactionManager())
}

class FlowTransactionManager() extends Actor {

  private[this] val log = Logger[FlowTransactionManager]

  private[this] implicit val timeout : Timeout = Timeout(3.seconds)
  private[this] implicit val eCtxt : ExecutionContext = context.system.dispatcher

  private[this] def transactionActor(id : String): Future[ActorRef] = {
    context.system.actorSelection("/user/" + id ).resolveOne()(100.millis)
  }

  private[this] def fwdToTransaction(id : String, m : Any)(implicit eCtxt : ExecutionContext) : Unit = {
    val respondTo : ActorRef = sender()

    transactionActor(id).map { a =>
      a.tell(m, respondTo)
      Done
    }.onComplete {
      case Success(_) =>
      case Failure(_) => log.warn(s"Error forwarding message [$m] to transaction actor [$id]")
    }
  }

  override def receive: Receive = {

    case e : FlowTransactionEvent =>
      val respondTo = sender()

      log.trace(s"Recording transaction event [$e]")

      transactionActor(e.transactionId).recoverWith[ActorRef] {
        case t : Throwable =>
          log.debug(s"Creating new Transaction actor for [${e.transactionId}]")

          val a = context.system.actorOf(FlowTransactionActor.props(
            FlowTransaction(
              id = e.transactionId,
              creationProps = e.properties
            )
          ), e.transactionId)
          Future(a)
      }.map(a => a.tell(e, respondTo))

    case RestartTransactionActor(id) => fwdToTransaction(id, RestartActor)

    case state @ FlowTransactionActor.State(tid) => fwdToTransaction(tid, state)

    case m => log.warn(s"Unhandled msg [$m] ")
  }
}
