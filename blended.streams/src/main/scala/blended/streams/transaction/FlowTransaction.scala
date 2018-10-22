package blended.streams.transaction

import java.util.UUID

import blended.streams.message.FlowEnvelope
import blended.streams.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.worklist.WorklistState
import blended.streams.worklist.WorklistState.WorklistState
import blended.util.logging.Logger

import scala.util.Try

class MismatchedFlowTransactionException(t: FlowTransaction, evt: FlowTransactionEvent)
  extends Exception(s"FlowTransaction [${t.tid}] did not match event [${evt.transactionId}]")

case class FlowTransaction private [transaction](
  startedWith : Option[FlowEnvelope],
  worklist : Map[String, WorklistState] = Map.empty,
  stateOverride : Option[FlowTransactionState] = None
) {
  val tid : String = startedWith.map(_.id).getOrElse(UUID.randomUUID().toString())

  def state : FlowTransactionState = stateOverride match {
    // if the state was overridden, this IS the state regardless of the worklist
    case Some(s) => s
    // otherwise we will dtermine it from the envelope that has kicked off the transaction
    // and the current worklist
    case None => startedWith match {
      case None => FlowTransactionState.Started
      case Some(env) =>
        if (worklist.values.exists(v => v == WorklistState.Failed || v == WorklistState.TimeOut)) {
          FlowTransactionState.Failed
        } else {
          val states = worklist.values.toSeq.distinct
          if (states.size > 1) {
            FlowTransactionState.Updated
          } else {
            states.head match {
              case c : WorklistState if c == WorklistState.Completed => FlowTransactionState.Completed
              case s : WorklistState if s == WorklistState.Started => FlowTransactionState.Started
            }
          }
        }
    }
  }
}

object FlowTransaction {

  private[this] val log = Logger[FlowTransaction]

  def startTransaction(env : Option[FlowEnvelope] = None) : FlowTransaction =
    FlowTransaction(startedWith = env)

  private[this] def itemIds(header : String, env: FlowEnvelope*) : Try[Seq[String]] = Try {
    env.map(e => e.header[String](header).get)
  }

  def updateTransaction(
    transaction : FlowTransaction,
    event : FlowTransactionEvent,
    subTidHeader : String,
    itemState : WorklistState,
    strict : Boolean = true
  ) : Try[FlowTransaction] = Try {
    transaction.tid match {
      case id if id == event.transactionId =>

        event match {
          case started: FlowTransactionStarted =>
            log.warn(s"Ignoring started event for open transaction [${transaction.tid}]")
            transaction

          case completed : FlowTransactionFailed => transaction.copy(
            stateOverride = Some(FlowTransactionState.Completed),
            worklist = Map.empty
          )

          case failed : FlowTransactionFailed => transaction.copy(
            stateOverride = Some(FlowTransactionState.Failed),
            worklist = Map.empty
          )

          case updated : FlowTransactionUpdate =>
            // We extract the id's for the transaction parts
            val updatedItemIds : Seq[String] = itemIds(subTidHeader, updated.envelopes:_*).get
            val unmatched : Seq[String] = updatedItemIds.filter { id => !transaction.worklist.contains(id) }

            val newWorklist =
              // We keep everything that is not in the update
              transaction.worklist.filterKeys { id => !updatedItemIds.contains(id) } ++
              // and add everything from the update list with the new state
              updatedItemIds.map(id => id -> itemState).toMap

            transaction.copy(worklist = newWorklist)
        }

      case id =>
        val err = new MismatchedFlowTransactionException(transaction, event)
        if (strict) {
          throw err
        } else {
          log.warn(err)(err.getMessage())
          transaction
        }
    }
  }
}
