package blended.streams.worklist

import java.util.UUID

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import blended.streams.message.FlowEnvelope
import blended.streams.worklist.WorklistState.WorklistState
import blended.util.logging.Logger

import scala.collection.mutable
import scala.concurrent.duration._

object WorklistState extends Enumeration {
  type WorklistState = Value
  val Started, Completed, TimeOut, Failed = Value
}

trait WorklistItem {
  def id: String
}

case class FlowWorklistItem(env: FlowEnvelope, outboundId: String) extends WorklistItem {
  override def id: String = env.id + ":" + outboundId
}

case class Worklist(
  id: String = UUID.randomUUID().toString(),
  items: Seq[WorklistItem]
)

sealed trait WorklistEvent {
  def worklist: Worklist
  def state : WorklistState
}

case class WorklistStarted(worklist: Worklist, timeout: FiniteDuration = 100.millis, state : WorklistState = WorklistState.Started) extends WorklistEvent
case class WorklistStepCompleted(worklist: Worklist, state: WorklistState = WorklistState.Completed) extends WorklistEvent
case class WorklistTerminated(worklist: Worklist, state: WorklistState) extends WorklistEvent

object WorklistManager {

  def flow(name: String): Flow[WorklistEvent, WorklistEvent, NotUsed] =
    Flow.fromGraph(new WorklistGraphStage(name))

  private class WorklistGraphStage(name: String) extends GraphStage[FlowShape[WorklistEvent, WorklistEvent]] {

    private val in = Inlet[WorklistEvent](s"$name.in")
    private val out = Outlet[WorklistEvent](s"$name.out")

    override def shape: FlowShape[WorklistEvent, WorklistEvent] = FlowShape(in, out)

    private object CurrentItemState {
      def apply(item: WorklistItem): CurrentItemState = CurrentItemState(item, WorklistState.Started, System.currentTimeMillis())
    }

    private case class CurrentItemState(
      item: WorklistItem,
      state: WorklistState,
      created: Long
    )

    private object CurrentWorklistState {
      def apply(
        started: WorklistStarted
      ): CurrentWorklistState = new CurrentWorklistState(
        started.worklist.id,
        mutable.Map(started.worklist.items.map(i => i.id -> CurrentItemState(i)): _*),
        timeout = started.timeout
      )
    }

    private case class CurrentWorklistState(
      id: String,
      items: mutable.Map[String, CurrentItemState],
      timeout: FiniteDuration
    )

    private object Tick

    /* -----------------------------------------------------------------------------------------*/
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape) {

      private val activeWorklists: mutable.Map[String, CurrentWorklistState] = mutable.Map.empty
      private val outEvents: mutable.Queue[WorklistEvent] = mutable.Queue.empty

      private val log = Logger[WorklistManager.type]

      override def preStart(): Unit = {
        schedulePeriodically(Tick, 100.millis)
        pull(in)
      }

      override def postStop(): Unit = {
        cancelTimer(Tick)
      }

      override protected def onTimer(timerKey: Any): Unit = {

        timerKey match {
          case Tick =>
            activeWorklists.values.foreach { awl =>
              val now = System.currentTimeMillis()
              // We only examine items in started state
              val timedOut = awl.items.values
                .filter(i => i.state == WorklistState.Started)
                // and of those we want only the timed out ones
                .filter(i => now - i.created > awl.timeout.toMillis)

              // Then we update the timed out items in the worklist
              timedOut.foreach(i => awl.items.put(i.item.id, i.copy(state = WorklistState.TimeOut)))

              // finally we check the awl
              checkWorklist(awl)
            }
          case _ =>
        }
      }

      private def pushEvent(event: WorklistEvent): Unit = {
        log.debug(s"Pushing event [$event] for worklist")
        if (outEvents.isEmpty && isAvailable(out)) {
          push(out, event)
        } else {
          outEvents.enqueue(event)
        }
      }

      private def startWorklist(event: WorklistStarted): Unit = {
        activeWorklists.get(event.worklist.id) match {
          case None =>
            activeWorklists += (event.worklist.id -> CurrentWorklistState(event))
            pushEvent(event)
          case Some(awl) =>
            log.warn(s"Received start event for worklist [${awl.id}] that is already in use, ignoring StartWorklist event")
        }
      }

      def sendEvent(wl: CurrentWorklistState, state: WorklistState): Unit = {
        val event = WorklistTerminated(
          worklist = Worklist(id = wl.id, wl.items.values.map(_.item).toSeq),
          state = state
        )
        pushEvent(event)
        activeWorklists -= wl.id
        log.debug(s"Sent WorklistEvent [$event], [${activeWorklists.size}] remaining worklists ")
      }

      private def checkWorklist(wl: CurrentWorklistState): Unit = {

        val existsByState: WorklistState => Boolean = state => wl.items.exists(i => i._2.state == state)

        // First we check for timeouts
        existsByState(WorklistState.TimeOut) match {
          case true =>
            sendEvent(wl, WorklistState.TimeOut)
          case false =>
            // We have failed if at least one item has failed
            existsByState(WorklistState.Failed) match {
              case true =>
                sendEvent(wl, WorklistState.Failed)
              case false =>
                // We have handled all errors here
                existsByState(WorklistState.Started) match {
                  // we still have unprocessed items in this worklist
                  case true =>
                    activeWorklists.put(wl.id, wl)
                  // Everything that is not started, must have completed, so we are done
                  case false =>
                    sendEvent(wl, WorklistState.Completed)
                }
            }
        }
      }

      private def processSteps(event: WorklistStepCompleted): Unit = {
        activeWorklists.get(event.worklist.id) match {
          case None =>
            log.warn(s"Worklist [${event.worklist.id}] is not active, ignoring event [$event]")
          case Some(awl) =>
            // Event should be anything but started
            if (event.state == WorklistState.Started) {
              log.warn(s"Unexpected state [${event.state}] in process complete event [$event]")
            } else {

              val filter: String => Boolean = id => awl.items.isDefinedAt(id)

              val eventIds = event.worklist.items.map(_.id).filter(filter)

              val missingEventIds = event.worklist.items.map(_.id).filterNot(filter)
              if (missingEventIds.nonEmpty) {
                log.warn(s"Event references untracked item ids [${missingEventIds.mkString(",")}] for [$event]")
              }

              val updatedItems: Seq[CurrentItemState] = eventIds.flatMap { id =>
                awl.items.get(id).toSeq.map(_.copy(state = event.state))
              }

              val newAwl = awl.copy(
                items = mutable.Map(
                  (awl.items.filterKeys(k => !eventIds.contains(k)) ++ updatedItems.map(i => i.item.id -> i)).toSeq: _*
                )
              )

              checkWorklist(newAwl)
            }
        }
      }

      private def terminateWorklist(event: WorklistTerminated): Unit = {
        activeWorklists.get(event.worklist.id) match {
          case None =>
          case Some(awl) => sendEvent(awl, event.state)
        }
      }

      setHandler(
        in, new InHandler {
          override def onPush(): Unit = {
            val event = grab(in)

            event match {
              case s: WorklistStarted => startWorklist(s)
              case s: WorklistStepCompleted => processSteps(s)
              case s: WorklistTerminated => terminateWorklist(s)
            }

            pull(in)
          }
        }
      )

      setHandler(
        out, new OutHandler {
          // If we have any pending events, push then down stream
          override def onPull(): Unit = if (outEvents.nonEmpty) {
            push(out, outEvents.dequeue())
          }
        }
      )

    }
  }
}
