package blended.streams.transaction

import java.util.UUID

import blended.streams.message.{FlowEnvelope, MsgProperty}
import blended.streams.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.worklist.WorklistState
import blended.streams.worklist.WorklistState.WorklistState
import blended.util.logging.Logger

import scala.util.Try

object FlowTransaction {

  def apply(env : Option[FlowEnvelope]) : FlowTransaction = {
    env match {
      case None => FlowTransaction(id = UUID.randomUUID().toString(), creationProps = Map.empty)
      case Some(e) => FlowTransaction(id = e.id, creationProps = e.flowMessage.header)
    }
  }

  def startEvent(env: Option[FlowEnvelope] = None): FlowTransactionStarted = {
    val t = apply(env)
    FlowTransactionStarted(
      transactionId = t.tid,
      creationProperties = t.creationProps
    )
  }

  val transaction2envelope : FlowHeaderConfig => FlowTransaction => FlowEnvelope = { cfg => t =>
    FlowEnvelope(t.creationProps, t.tid)
      .withHeader(cfg.headerTrans, t.tid).get
      .withHeader(cfg.headerState, t.state.toString).get
      .withHeader(cfg.headerBranch, t.worklist.map { case (k,v) => s"$k=$v" }.mkString(",")).get
  }

  val envelope2Transaction : FlowHeaderConfig => FlowEnvelope => FlowTransaction = { cfg => env =>
    val state = env.header[String](cfg.headerState).map(FlowTransactionState.withName).getOrElse(FlowTransactionState.Started)
    val worklistState : Map[String, WorklistState] = env.header[String](cfg.headerBranch).map { s =>
      if (s.isEmpty) {
        Map.empty[String, WorklistState]
      } else {
        s.split(",")
          .map(b => b.split("="))
          .filter(_.size == 2)
          .map(b => (b(0), WorklistState.withName(b(1))))
          .toMap
      }
    }.getOrElse(Map.empty[String, WorklistState])

    FlowTransaction(id = env.id, creationProps = env.flowMessage.header, state = state, worklist = worklistState)
  }
}

case class FlowTransaction private [transaction](
  id : String,
  creationProps : Map[String, MsgProperty[_]],
  worklist : Map[String, WorklistState] = Map.empty,
  state : FlowTransactionState = FlowTransactionState.Started,
) {

  val tid : String = id

  private[this] val log = Logger[FlowTransaction]

  def terminated: Boolean = state == FlowTransactionState.Completed || state == FlowTransactionState.Failed

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

  def updateTransaction(
    event : FlowTransactionEvent
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
        val updatedItemIds : Seq[String] = updated.branchIds

        val newWorklist : Map[String, WorklistState] =
        // We keep everything that is not in the update
          worklist.filterKeys { id => !updatedItemIds.contains(id) } ++
            // and add everything from the update list with the new state
            updatedItemIds.map(id => id -> updated.updatedState).toMap

        copy(worklist = newWorklist, state = worklistState(newWorklist))
    }
  }
}


