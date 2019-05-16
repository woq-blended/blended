package blended.streams.transaction

import java.util.UUID

import blended.streams.message.{FlowEnvelope, MsgProperty}
import blended.streams.transaction.FlowTransactionState.FlowTransactionState
import blended.streams.worklist.WorklistState
import blended.streams.worklist.WorklistState.WorklistState
import blended.util.logging.Logger

import scala.util.Try

object FlowTransaction {

  val branchSeparator : String = ","
  val stateSeparator : String = ":"

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
      properties = t.creationProps
    )
  }

  val transaction2envelope : FlowHeaderConfig => FlowTransaction => FlowEnvelope = { cfg => t =>
    FlowEnvelope(t.creationProps, t.tid)
      .withHeader(cfg.headerTransId, t.tid).get
      .withHeader(cfg.headerState, t.state.toString).get
      .withHeader(cfg.headerBranch, t.worklist.map { case (k,v) => s"$k=${v.mkString(stateSeparator)}" }.mkString(branchSeparator)).get
  }

  val envelope2Transaction : FlowHeaderConfig => FlowEnvelope => FlowTransaction = { cfg => env =>
    val state = env.header[String](cfg.headerState).map(FlowTransactionState.withName).getOrElse(FlowTransactionState.Started)
    val worklistState : Map[String, List[WorklistState]] = env.header[String](cfg.headerBranch).map { s =>
      if (s.isEmpty) {
        Map.empty[String, List[WorklistState]]
      } else {

        val branches : Map[String, List[String]] =
          s.split(branchSeparator)
            .map(b => b.split("="))
            .filter(_.length == 2)
            .map{ b =>
              b(0) -> b(1).split(stateSeparator).toList
            }.toMap

        branches.mapValues(s => s.map(WorklistState.withName))
      }
    }.getOrElse(Map.empty[String, List[WorklistState]])

    FlowTransaction(id = env.id, creationProps = env.flowMessage.header, state = state, worklist = worklistState)
  }

  private[transaction] def worklistState(currentState : FlowTransactionState, wl : Map[String, List[WorklistState]]) : List[FlowTransactionState] = {
    wl.map { case (_,v) =>
      if (v.contains(WorklistState.Failed) || v.contains(WorklistState.TimeOut)) {
        FlowTransactionState.Failed
      } else if (v.contains(WorklistState.Started) && v.contains(WorklistState.Completed)) {
        FlowTransactionState.Completed
      } else if (v.contains(WorklistState.Started)) {
        FlowTransactionState.Started
      } else if (v.contains(WorklistState.Completed)) {
        FlowTransactionState.Updated
      } else {
        currentState
      }
    }.toList.distinct
  }

  private[transaction] def transactionState(currentState : FlowTransactionState, wl : Map[String, List[WorklistState]]): FlowTransactionState = {

    val itemStates : List[FlowTransactionState] = FlowTransaction.worklistState(currentState, wl)

    if (itemStates.contains(FlowTransactionState.Failed)) {
      FlowTransactionState.Failed
    } else if (itemStates.size > 1) {
      FlowTransactionState.Updated
    } else if (itemStates.equals(List(FlowTransactionState.Updated))) {
      FlowTransactionState.Updated
    } else if (itemStates.equals(List(FlowTransactionState.Completed))) {
      FlowTransactionState.Completed
    } else {
      currentState
    }
  }
}

case class FlowTransaction private [transaction](
  id : String,
  creationProps : Map[String, MsgProperty],
  worklist : Map[String, List[WorklistState]] = Map.empty,
  state : FlowTransactionState = FlowTransactionState.Started
) {

  override def toString: String = {
    val wlString = worklist.mkString(",")
    s"FlowTransaction[$state][$id][$wlString][$creationProps]"
  }

  val tid : String = id

  private[this] val log = Logger[FlowTransaction]

  def terminated: Boolean = state == FlowTransactionState.Completed || state == FlowTransactionState.Failed

  def updateTransaction(
    event : FlowTransactionEvent
  ) : Try[FlowTransaction] = Try {

    if (event.transactionId == tid) {
      log.trace(s"Updating transaction with [$event]")
      event match {

        case started: FlowTransactionStarted =>
          copy(creationProps = started.properties)
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
          val updatedItemIds : Map[String, List[WorklistState]] =
            updated.branchIds
              .map{ id => id -> List(updated.updatedState) }
              .map{ case (k, s) =>
                val oldState : List[WorklistState] = worklist.getOrElse(k, List.empty)
                k -> (s ::: oldState).distinct
              }
              .toMap

          // We keep everything that is not in the update
          val newWorklist : Map[String, List[WorklistState]] =
            worklist.filterKeys { id => !updatedItemIds.contains(id) } ++ updatedItemIds

          copy(worklist = newWorklist, state = FlowTransaction.transactionState(state, newWorklist))
      }
    } else {
      this
    }
  }
}
