package blended.streams.transaction

import java.util.{Date, UUID}

import blended.streams.message.{FlowEnvelope, MsgProperty}
import blended.streams.worklist._
import blended.util.logging.Logger

object FlowTransaction {

  val branchSeparator : String = ","
  val stateSeparator : String = ":"

  def apply(env : Option[FlowEnvelope]) : FlowTransaction = {
    val now : Date = new Date()

    env match {
      case None =>
        FlowTransaction(
          id = UUID.randomUUID().toString(),
          created = now,
          lastUpdate = now,
          creationProps = Map.empty
        )

      case Some(e) =>
        FlowTransaction(
          id = e.id,
          created = now,
          lastUpdate = now,
          creationProps = e.flowMessage.header
        )
    }
  }

  def startEvent(env : Option[FlowEnvelope] = None) : FlowTransactionStarted = {
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
      .withHeader(cfg.headerTransCreated, t.created.getTime()).get
      .withHeader(cfg.headerTransUpdated, t.lastUpdate.getTime()).get
      .withHeader(cfg.headerBranch, t.worklist.map { case (k,v) => s"$k=${v.mkString(stateSeparator)}" }.mkString(branchSeparator)).get
  }

  val envelope2Transaction : FlowHeaderConfig => FlowEnvelope => FlowTransaction = { cfg => env =>
    val state : FlowTransactionState =
      env.header[String](cfg.headerState).map(h => FlowTransactionState.apply(h).get).getOrElse(FlowTransactionStateStarted)

    val worklistState : Map[String, List[WorklistState]] = env.header[String](cfg.headerBranch).map { s =>
      if (s.isEmpty) {
        Map.empty[String, List[WorklistState]]
      } else {

        val branches : Map[String, List[String]] =
          s.split(branchSeparator)
            .map(b => b.split("="))
            .filter(_.length == 2)
            .map { b =>
              b(0) -> b(1).split(stateSeparator).toList
            }.toMap

        branches.mapValues(s => s.map(i => WorklistState.apply(i).get))
      }
    }.getOrElse(Map.empty[String, List[WorklistState]])

    val created : Date = new Date(env.header[Long](cfg.headerTransCreated).getOrElse(System.currentTimeMillis()))
    val updated : Date = new Date(env.header[Long](cfg.headerTransUpdated).getOrElse(created.getTime()))

    FlowTransaction(
      id = env.id,
      created = created,
      lastUpdate = updated,
      creationProps = env.flowMessage.header,
      state = state, worklist = worklistState
    )
  }

  private[transaction] def worklistState(currentState : FlowTransactionState, wl : Map[String, List[WorklistState]]) : List[FlowTransactionState] = {
    wl.map { case (_,v) =>
      if (v.contains(WorklistStateFailed) || v.contains(WorklistStateTimeout)) {
        FlowTransactionStateFailed
      } else if (v.contains(WorklistStateStarted) && v.contains(WorklistStateCompleted)) {
        FlowTransactionStateCompleted
      } else if (v.contains(WorklistStateStarted)) {
        FlowTransactionStateStarted
      } else if (v.contains(WorklistStateCompleted)) {
        FlowTransactionStateUpdated
      } else {
        currentState
      }
    }.toList.distinct
  }

  private[transaction] def transactionState(currentState : FlowTransactionState, wl : Map[String, List[WorklistState]]) : FlowTransactionState = {

    val itemStates : List[FlowTransactionState] = FlowTransaction.worklistState(currentState, wl)

    if (itemStates.contains(FlowTransactionStateFailed)) {
      FlowTransactionStateFailed
    } else if (itemStates.size > 1) {
      FlowTransactionStateUpdated
    } else if (itemStates.equals(List(FlowTransactionStateUpdated))) {
      FlowTransactionStateUpdated
    } else if (itemStates.equals(List(FlowTransactionStateCompleted))) {
      FlowTransactionStateCompleted
    } else {
      if (currentState == FlowTransactionStateStarted) {
        FlowTransactionStateUpdated
      } else {
        currentState
      }
    }
  }
}

case class FlowTransaction(
  id : String,
  created : Date,
  lastUpdate : Date,
  creationProps : Map[String, MsgProperty],
  worklist : Map[String, List[WorklistState]] = Map.empty,
  state : FlowTransactionState = FlowTransactionStateStarted
) {

  override def toString : String = {
    val wlString = worklist.mkString(",")
    s"FlowTransaction[$state][$id][$wlString][$creationProps]"
  }

  val tid : String = id

  private[this] val log = Logger[FlowTransaction]

  def terminated: Boolean = state == FlowTransactionStateCompleted || state == FlowTransactionStateFailed

  private def applyStarted(started : FlowTransactionStarted) : FlowTransaction = copy(
    lastUpdate = new Date(),
    creationProps = started.properties,
    state = FlowTransactionStateUpdated
  )

  private def applyCompleted() : FlowTransaction = copy(
    lastUpdate = new Date(),
    state = FlowTransactionStateCompleted,
    worklist = Map.empty
  )

  private def applyFailed() : FlowTransaction = copy(
    lastUpdate = new Date(),
    state = FlowTransactionStateFailed,
    worklist = Map.empty
  )

  private def applyUpdate(updated : FlowTransactionUpdate) : FlowTransaction = {
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

    copy(
      worklist = newWorklist,
      lastUpdate = new Date(),
      state = FlowTransaction.transactionState(state, newWorklist)
    )
  }

  private val applyEvent : FlowTransactionEvent => FlowTransaction = { event =>
    if (event.transactionId == tid) {
      if (state == FlowTransactionStateStarted || state == FlowTransactionStateUpdated) {
        log.trace(s"Updating transaction [$tid] with [$event]")
        event match {
          case started: FlowTransactionStarted => applyStarted(started)
          case _ : FlowTransactionCompleted => applyCompleted()
          case _ : FlowTransactionFailed => applyFailed()
          case updated : FlowTransactionUpdate => applyUpdate(updated)
        }
      } else {
        log.trace(s"Ignoring event for already terminated transaction [$tid]")
        this
      }
    } else {
      this
    }
  }

  def updateTransaction(event : FlowTransactionEvent) : FlowTransaction = applyEvent(event)
}
