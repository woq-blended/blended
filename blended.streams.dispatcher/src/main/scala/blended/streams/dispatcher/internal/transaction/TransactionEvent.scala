package blended.streams.dispatcher.internal.transaction

import blended.streams.dispatcher.internal.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.dispatcher.internal.worklist.WorklistItem
import blended.streams.message.FlowEnvelope

object TransactionEvent {

  def fromEnvelope(envelope: FlowEnvelope, items: WorklistItem*) : TransactionEvent = {
    TransactionEvent(
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
