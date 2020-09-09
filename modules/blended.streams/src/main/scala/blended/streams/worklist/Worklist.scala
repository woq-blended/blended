package blended.streams.worklist

import java.util.UUID

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

sealed trait WorklistState {
  val name : String
  override def toString: String = name
}

case object WorklistStateCompleted extends WorklistState{ override val name: String = "Completed" }
case object WorklistStateStarted extends WorklistState{ override val name: String = "Started" }
case object WorklistStateTimeout extends WorklistState{ override val name: String = "Timeout" }
case object WorklistStateFailed extends WorklistState{ override val name: String = "Failed" }

object WorklistState {
  def apply(s : String) : Try[WorklistState] = Try {
    s match {
      case "Completed" => WorklistStateCompleted
      case "Started" => WorklistStateStarted
      case "Timeout" => WorklistStateTimeout
      case "Failed" => WorklistStateFailed
      case _ => throw new IllegalArgumentException(s"[$s] is not a valid Worklist State")
    }
  }
}

trait WorklistItem {
  def id : String
}

case class FlowWorklistItem(env : FlowEnvelope, outboundId : String) extends WorklistItem {
  override def id : String = env.id + ":" + outboundId
}

case class Worklist(
  id : String = UUID.randomUUID().toString(),
  items : Seq[WorklistItem]
)

sealed trait WorklistEvent {
  def worklist : Worklist
  def state : WorklistState
}

final case class WorklistStarted(worklist: Worklist, timeout: FiniteDuration = 100.millis, state : WorklistState = WorklistStateStarted) extends WorklistEvent
final case class WorklistStepCompleted(worklist: Worklist, state: WorklistState = WorklistStateCompleted) extends WorklistEvent
final case class WorklistTerminated(worklist: Worklist, state: WorklistState, reason: Option[Throwable]) extends WorklistEvent

object WorklistManager {

  def flow(name: String, log : FlowEnvelopeLogger): Flow[WorklistEvent, WorklistEvent, NotUsed] =
    Flow.fromGraph(new WorklistGraphStage(name, log))

  private class WorklistGraphStage(name: String, log : FlowEnvelopeLogger) extends GraphStage[FlowShape[WorklistEvent, WorklistEvent]] {

    private val in = Inlet[WorklistEvent](s"$name.in")
    private val out = Outlet[WorklistEvent](s"$name.out")

    override def shape : FlowShape[WorklistEvent, WorklistEvent] = FlowShape(in, out)

    private object CurrentItemState {
      def apply(item: WorklistItem): CurrentItemState = CurrentItemState(item, WorklistStateStarted, System.currentTimeMillis())
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
        mutable.Map(started.worklist.items.map(i => i.id -> CurrentItemState(i)) : _*),
        timeout = started.timeout
      )
    }

    private case class CurrentWorklistState(
      id : String,
      items : mutable.Map[String, CurrentItemState],
      timeout : FiniteDuration
    )

    private object Tick

    /* -----------------------------------------------------------------------------------------*/
    override def createLogic(inheritedAttributes : Attributes) : GraphStageLogic = new TimerGraphStageLogic(shape) {

      private val activeWorklists : mutable.Map[String, CurrentWorklistState] = mutable.Map.empty
      private val outEvents : mutable.Queue[WorklistEvent] = mutable.Queue.empty

      override def preStart() : Unit = {
        scheduleAtFixedRate(Tick, 100.millis, 100.millis)
        pull(in)
      }

      override def postStop() : Unit = {
        cancelTimer(Tick)
      }

      override protected def onTimer(timerKey : Any) : Unit = {

        timerKey match {
          case Tick =>
            activeWorklists.values.foreach { awl =>
              val now = System.currentTimeMillis()
              // We only examine items in started state
              val timedOut = awl.items.values
                .filter(i => i.state == WorklistStateStarted)
                // and of those we want only the timed out ones
                .filter(i => now - i.created > awl.timeout.toMillis)

              // Then we update the timed out items in the worklist
              timedOut.foreach(i => awl.items.put(i.item.id, i.copy(state = WorklistStateTimeout)))

              // finally we check the awl
              checkWorklist(awl)
            }
          case _ =>
        }
      }

      private def pushEvent(event : WorklistEvent) : Unit = {
        if (outEvents.isEmpty && isAvailable(out)) {
          push(out, event)
        } else {
          outEvents.enqueue(event)
        }
      }

      private def startWorklist(event : WorklistStarted) : Unit = {
        activeWorklists.get(event.worklist.id) match {
          case None =>
            log.underlying.debug(s"Starting Worklist [${event.worklist.id}]")
            activeWorklists += (event.worklist.id -> CurrentWorklistState(event))
            pushEvent(event)
          case Some(awl) =>
            log.underlying.warn(s"Received start event for worklist [${awl.id}] that is already in use, ignoring StartWorklist event")
        }
      }

      def sendEvent(wl : CurrentWorklistState, state : WorklistState) : Unit = {
        // Todo : Set fail reason
        val event = WorklistTerminated(
          worklist = Worklist(id = wl.id, wl.items.values.map(_.item).toSeq),
          state = state,
          reason = None
        )
        pushEvent(event)
        activeWorklists -= wl.id
        log.underlying.debug(s"Sent WorklistEvent [$event], [${activeWorklists.size}] remaining worklists ")
      }

      private def checkWorklist(wl : CurrentWorklistState) : Unit = {

        val existsByState : WorklistState => Boolean = state => wl.items.exists(i => i._2.state == state)

        // First we check for timeouts
        existsByState(WorklistStateTimeout) match {
          case true =>
            sendEvent(wl, WorklistStateTimeout)
          case false =>
            // We have failed if at least one item has failed
            existsByState(WorklistStateFailed) match {
              case true =>
                sendEvent(wl, WorklistStateFailed)
              case false =>
                // We have handled all errors here
                existsByState(WorklistStateStarted) match {
                  // we still have unprocessed items in this worklist
                  case true =>
                  // Everything that is not started, must have completed, so we are done
                  case false =>
                    sendEvent(wl, WorklistStateCompleted)
                }
            }
        }
      }

      private def processSteps(event : WorklistStepCompleted) : Unit = {
        activeWorklists.get(event.worklist.id) match {
          case None =>
            log.underlying.trace(s"Worklist [${event.worklist.id}] is not active, ignoring event [$event]")
          case Some(awl) =>
            // Event should be anything but started
            if (event.state == WorklistStateStarted) {
              log.underlying.trace(s"Unexpected state [${event.state}] in process complete event [$event]")
            } else {

              val filter : String => Boolean = id => awl.items.isDefinedAt(id)

              val missingEventIds = event.worklist.items.map(_.id).filterNot(filter)
              if (missingEventIds.nonEmpty) {
                log.underlying.warn(s"Event references untracked item ids [${missingEventIds.mkString(",")}] for [$event]")
              }

              val newEvents : Map[String, CurrentItemState] = event.worklist.items.filter(i => filter(i.id)).map { i =>
                val s = awl.items.get(i.id).get
                val newItem = s.copy(item = i, state = event.state)
                (newItem.item.id -> newItem)
              }.toMap

              val newAwl = awl.copy(
                items = awl.items.filter { case (k, _) => !newEvents.contains(k) } ++ newEvents
              )

              activeWorklists.put(event.worklist.id, newAwl)
              checkWorklist(newAwl)
            }
        }
      }

      private def terminateWorklist(event : WorklistTerminated) : Unit = {
        activeWorklists.get(event.worklist.id) match {
          case None      =>
          case Some(awl) => sendEvent(awl, event.state)
        }
      }

      setHandler(
        in, new InHandler {
        override def onPush() : Unit = {
          val event = grab(in)

          event match {
            case s : WorklistStarted       => startWorklist(s)
            case s : WorklistStepCompleted => processSteps(s)
            case s : WorklistTerminated    => terminateWorklist(s)
          }

          pull(in)
        }
      }
      )

      setHandler(
        out, new OutHandler {
        // If we have any pending events, push then down stream
        override def onPull() : Unit = if (outEvents.nonEmpty) {
          push(out, outEvents.dequeue())
        }
      }
      )

    }
  }
}
