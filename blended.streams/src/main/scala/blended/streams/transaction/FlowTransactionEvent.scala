package blended.streams.transaction

import blended.streams.message.FlowEnvelope
import blended.streams.transaction.FlowTransactionState.FlowTransactionState

sealed trait FlowTransactionEvent {
  def envelopes : Seq[FlowEnvelope]
  def transactionId : String
  def state : FlowTransactionState
}

case class FlowTransactionStarted private[transaction](
  override val transactionId : String,
  override val envelopes : FlowEnvelope*
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Started
}

case class FlowTransactionUpdate private[transaction](
  override val transactionId : String,
  updatedState : FlowTransactionState,
  override val envelopes : FlowEnvelope*
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Updated
}

case class FlowTransactionFailed private[transaction](
  override val transactionId : String,
  override val envelopes : FlowEnvelope*
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Failed
}

final case class FlowTransactionCompleted private[transaction](
  override val transactionId : String,
  override val envelopes : FlowEnvelope*
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Completed
}
