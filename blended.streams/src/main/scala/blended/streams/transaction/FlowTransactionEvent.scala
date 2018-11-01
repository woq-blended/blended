package blended.streams.transaction

import blended.streams.message.{FlowEnvelope, MsgProperty}
import blended.streams.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.worklist.WorklistState.WorklistState

sealed trait FlowTransactionEvent {
  def transactionId : String
  def state : FlowTransactionState
}

case class FlowTransactionStarted(
  override val transactionId : String,
  creationProperties : Map[String, MsgProperty[_]]
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Started
}

case class FlowTransactionUpdate(
  override val transactionId : String,
  updatedState : WorklistState,
  branchIds : String*
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Updated
}

case class FlowTransactionFailed(
  override val transactionId : String,
  reason : Option[Throwable]
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Failed
}

final case class FlowTransactionCompleted (
  override val transactionId : String
) extends FlowTransactionEvent {
  override val state: FlowTransactionState = FlowTransactionState.Completed
}
