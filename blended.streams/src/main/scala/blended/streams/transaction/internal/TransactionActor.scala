package blended.streams.transaction.internal

import akka.Done
import akka.actor.{Actor, ActorRef, Props}
import akka.persistence.SnapshotOffer
import akka.util.Timeout
import blended.streams.persistence.RestartableActor
import blended.streams.persistence.RestartableActor.RestartActor
import blended.streams.transaction.internal.TransactionActor.State
import blended.streams.transaction.internal.TransactionManager.RestartTransactionActor
import blended.streams.transaction.{FlowTransaction, FlowTransactionEvent, FlowTransactionStarted}
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object TransactionActor {

  case class State(tid : String)

  def props(initialState: FlowTransaction) : Props =
    Props(new TransactionActor(initialState))
}

class TransactionActor(initialState: FlowTransaction) extends RestartableActor {

  private val log = Logger[TransactionActor]
  private var state : FlowTransaction = initialState
  private var persisted = false

  override def persistenceId: String = initialState.tid

  def updateState(evt: FlowTransactionEvent) : Unit = {
    state = state.updateTransaction(evt).get
    log.trace(s"New state is [$state]")
  }

  val transactionRecover : Receive = {
    case evt : FlowTransactionEvent =>
      log.trace(s"Replaying FlowTransactionEvent [$evt]")
      updateState(evt)
      persisted = true
    case SnapshotOffer(_, snapshot : FlowTransaction) => state = snapshot
  }

  override def receiveRecover: Receive = transactionRecover

  private[this] def processEvent(event : FlowTransactionEvent) = {
    persist(event){ e =>
      updateState(e)
      context.system.eventStream.publish(state)
      if (state.terminated) {
        context.stop(self)
      }
    }
  }

  private val cmdReceive : Receive = {

    case s : FlowTransactionStarted =>
      if (!persisted) {
        processEvent(s)
        persisted = true
      } else {
        context.system.eventStream.publish(state)
      }
    case e : FlowTransactionEvent =>
      processEvent(e)
    case State(_) =>
      log.trace("Sending state to " + sender().path)
      sender() ! state
  }

  override def receiveCommand: Receive = cmdReceive.orElse(restartReceive)
}

object TransactionManager {

  // TODO : Review this is only required for testing
  case class RestartTransactionActor(id: String)

  def props(branchHeader : String) : Props = Props(new TransactionManager(branchHeader))
}

class TransactionManager(branchHeader: String) extends Actor {

  private[this] val log = Logger[TransactionManager]

  private[this] implicit val timeout : Timeout = Timeout(3.seconds)
  private[this] implicit val eCtxt : ExecutionContext = context.system.dispatcher

  private[this] def transactionActor(id : String): Future[ActorRef] = {
    context.system.actorSelection("/user/" + id ).resolveOne()(100.millis)
  }

  private[this] def fwdToTransaction(id : String, m : Any)(implicit eCtxt : ExecutionContext) : Unit = {
    val respondTo : ActorRef = sender()

    transactionActor(id).map { a =>
      log.debug(s"Forwarding [$m] to [${a.path}]")
      a.tell(m, respondTo)
      Done
    }.onComplete {
      case Success(_) =>
      case Failure(t) => log.warn(t)(s"Error forwarding message [$m] to transaction actor [$id]")
    }
  }

  override def receive: Receive = {

    case s : FlowTransactionStarted =>
      transactionActor(s.transactionId).recoverWith[ActorRef] {
        case t : Throwable =>
          log.debug(s"Creating new Transaction actor for [${s.transactionId}]")

          val a = context.system.actorOf(TransactionActor.props(
            FlowTransaction(
              id = s.transactionId,
              creationProps = s.creationProperties
            )
          ), s.transactionId)

          a ! s
          Future(a)
      }

    case e : FlowTransactionEvent => fwdToTransaction(e.transactionId, e)

    case RestartTransactionActor(id) => fwdToTransaction(id, RestartActor)

    case state @ TransactionActor.State(tid) => fwdToTransaction(tid, state)

    case m => log.warn(s"Unhandled msg [$m] ")
  }
}
