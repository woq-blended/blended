package blended.streams.transaction

import java.util.UUID

import blended.streams.message.FlowEnvelope
import blended.streams.transaction
import blended.streams.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.worklist.WorklistState
import blended.streams.worklist.WorklistState.WorklistState
import blended.util.logging.Logger

import scala.util.Try

case class FlowTransaction private [transaction](
  startedWith : Option[FlowEnvelope],
  worklist : Map[String, WorklistState] = Map.empty,
  state : FlowTransactionState = FlowTransactionState.Started,
) {

  val tid : String = startedWith.map(_.id).getOrElse(UUID.randomUUID().toString())

  private[this] val log = Logger[FlowTransaction]

  def terminated = state == FlowTransactionState.Completed || state == FlowTransactionState.Failed

  private def worklistState(wl: Map[String, WorklistState]): FlowTransactionState = {
    if (wl.values.exists(v => v == WorklistState.Failed || v == WorklistState.TimeOut)) {
      FlowTransactionState.Failed
    } else {
      if (wl.values.exists(_ == WorklistState.Completed)) {
        if (wl.values.exists(_ == WorklistState.Started)) {
          FlowTransactionState.Updated
        } else {
          FlowTransactionState.Completed
        }
      } else {
        FlowTransactionState.Started
      }
    }
  }

  private[this] def itemIds(header : String, env: FlowEnvelope*) : Try[Seq[String]] = Try {
    env.map(e => e.header[String](header).get)
  }

  def updateTransaction(
    event : FlowTransactionEvent,
    subTidHeader : String
  ) : Try[FlowTransaction] = Try {
    event match {

      case started: FlowTransactionStarted =>
        log.warn(s"Ignoring started event for open transaction [$tid]")
        this

      case completed : FlowTransactionCompleted => copy(
        state = FlowTransactionState.Completed,
        worklist = Map.empty
      )

      case failed : FlowTransactionFailed => copy(
        state = FlowTransactionState.Failed,
        worklist = Map.empty
      )

      case updated : FlowTransactionUpdate =>
        // We extract the id's for the transaction parts
        val updatedItemIds : Seq[String] = itemIds(subTidHeader, updated.envelopes:_*).get

        val newWorklist : Map[String, WorklistState] =
        // We keep everything that is not in the update
          worklist.filterKeys { id => !updatedItemIds.contains(id) } ++
            // and add everything from the update list with the new state
            updatedItemIds.map(id => id -> updated.updatedState).toMap

        copy(worklist = newWorklist, state = worklistState(newWorklist))
    }
  }
}

object FlowTransaction {

  def startTransaction(env: Option[FlowEnvelope] = None): FlowTransactionStarted =
    FlowTransactionStarted(env.map(_.id).getOrElse(UUID.randomUUID().toString()), env)
}
