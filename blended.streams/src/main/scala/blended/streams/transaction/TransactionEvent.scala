package blended.streams.transaction

import blended.streams.message.FlowEnvelope
import blended.streams.transaction
import blended.streams.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.worklist.WorklistItem

object TransactionEvent {

  def fromEnvelope(envelope: FlowEnvelope, items: WorklistItem*) : TransactionEvent = {
    transaction.TransactionEvent(
      tid = envelope.headerWithDefault("TransactionId", envelope.id),
      envelope = envelope,
      state = FlowTransactionState.Started,
      worklist = items.map(i => (i.id -> i)).toMap
    )
  }
}

case class TransactionEvent(
  tid : String,
  envelope : FlowEnvelope,
  state : FlowTransactionState,
  worklist : Map[String, WorklistItem]
)
