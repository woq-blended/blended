package blended.streams.transaction.internal

import akka.actor.{Actor, Props}
import akka.persistence.{PersistentActor, SnapshotOffer}
import blended.streams.transaction.{FlowTransaction, FlowTransactionEvent, FlowTransactionStarted, FlowTransactionState}
import blended.util.logging.Logger

object TransactionActor {

  def props(initialState: FlowTransaction, branchHeader : String) : Props =
    Props(new TransactionActor(initialState, branchHeader))
}

class TransactionActor(initialState: FlowTransaction, branchHeader : String) extends PersistentActor {

  private var state : FlowTransaction = initialState
  private val id = initialState.tid

  override def persistenceId: String = initialState.tid

  def updateState(evt: FlowTransactionEvent) : Unit =
    state = state.updateTransaction(evt, branchHeader).get

  override def receiveRecover: Receive = {
    case evt : FlowTransactionEvent => updateState(evt)
    case SnapshotOffer(_, snapshot : FlowTransaction) => state = snapshot
  }

  override def receiveCommand: Receive = {
    case e : FlowTransactionEvent => persist(e){ e =>
      updateState(e)
      context.system.eventStream.publish(state)
      if (state.terminated) {
        context.stop(self)
      }
    }
  }
}

object TransactionManager {

  def props(branchHeader : String) : Props = Props(new TransactionManager(branchHeader))
}

class TransactionManager(branchHeader: String) extends Actor {

  private[this] val log = Logger[TransactionManager]

  override def receive: Receive = {
    case s : FlowTransactionStarted =>
      log.debug(s"Received [$s]")
      if (context.child(s.transactionId).isEmpty) {
        log.debug(s"Creating new Transaction actor for [${s.transactionId}]")
        val a = context.actorOf(TransactionActor.props(FlowTransaction(s.envelope), branchHeader), s.transactionId)
        a ! s
      }
    case e : FlowTransactionEvent =>
      context.child(e.transactionId).foreach(_ ! e)
  }
}
