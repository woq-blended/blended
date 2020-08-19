package blended.streams

import akka.stream.stage.{AsyncCallback, OutHandler, TimerGraphStageLogic}
import akka.stream.{Outlet, Shape}
import blended.streams.AckState.AckState
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope, FlowEnvelopeLogger}
import blended.util.logging.LogLevel

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * The state of an acknowledgement :
 * PENDING : The acknowledge or denial is still outstanding
 * DENIED : The associated envelope has been denied
 * ACKNOWLEDGED : The associated envelope has been acknowledged
 */
object AckState extends Enumeration {
  type AckState = Value
  val Pending, Acknowledged, Denied = Value
}

trait AcknowledgeContext {

  // The associated inflight id
  def inflightId : String
  // the acknowledge state (pending, acknowledged or denied)

  def envelope : FlowEnvelope

  def created : Long

  def acknowledge() : Unit

  def deny() : Unit
}

case class DefaultAcknowledgeContext(
  override val inflightId : String,
  override val envelope : FlowEnvelope,
  override val created : Long
) extends AcknowledgeContext {
  override def acknowledge() : Unit = ()
  override def deny(): Unit = ()
}

/**
  * Provide common logic for Source stages producing [[FlowEnvelope]]s requiring
  * acknowledgement handling.
  *
  * The AckSource logic encapsulates the underlying logic for an arbitrary Source
  * of FlowEnvelopes. The envelopes will be passed down stream and must be
  * acknowledged or denied eventually. An acknowledgement that takes too long,
  * will be treated as a denial as well.
  *
  * A concrete implementation must implement the actions to be executed upon acknowledgement
  * and denial. For example, a JMS source would use a JMS acknowledge on the underlying
  * JMS message and a session.recover() upon a denial. A file system based source may move or
  * delete the original file upon acknowledge and restore the original file upon denial.

  * After picking up an envelope from an external system, the envelope will be considered to
  * be inflight until either an acknowledge or deny has been called. The AckSource logic defines
  * the maximum number of messages that can be inflight at any moment in time. No further messages
  * will be pulled from the external system until a free inflight slot will become available.
  *
  * Concrete implementations must:
  *
  * - create and maintain any technical connections required to poll the external system
  * - map the inbound data to a FlowEnvelope
  * - implement the concrete actions for acknowledgement and denial
  *
  * We will use an AcknowledgeContext to hold an inflight envelope and the id of the inflight slot
  * it is using along with any additional that might be required to perform an acknowledge or denial.
  *
  * As a result, each poll of the external system will produce an Option[AcknowledgeContext] which
  * will then be inserted into a free inflight slot. As a consequence, polling of the external system
  * will only be performed if and only if a free inflight slot is available.

  * As long as free inflight slots are available and no external messages are available, the
  * poll will be executed \in regular intervals. Concrete implementations may overwrite the
  * the nextPoll() method to modify the calculation of the next polling occurrence.
*/
abstract class AckSourceLogic[T <: AcknowledgeContext](
  val shape : Shape,
  val out : Outlet[FlowEnvelope],
  val ackTimeout : FiniteDuration = 1.second
) extends TimerGraphStageLogic(shape) {

  private case object Poll
  private case object CheckAck

  override def preStart() : Unit = {
    super.preStart()
    scheduleAtFixedRate(CheckAck, 100.millis, 100.millis)
  }

  /** The id to identify the instance in the log files */
  protected val id : String

  protected val autoAcknowledge : Boolean = false

  /** A logger that must be defined by concrete implementations */
  protected def log : FlowEnvelopeLogger

  /** The id's of the available inflight slots */
  protected val inflightSlots : List[String]

  // The map of current inflight AcknowledgeContexts. An inflight slot is considered
  // to be available if it's id does not occur in the keys of the inflight map.
  private val inflightMap : mutable.Map[String, (T, AckState)] = mutable.Map.empty

  private var lastUsedSlot : Option[String] = None

  // TODO: Make this configurable ?
  protected def nextPoll() : Option[FiniteDuration] = Some(1.second)

  // A callback to fail the stage
  protected val fail : AsyncCallback[Throwable]= getAsyncCallback[Throwable]{ t : Throwable =>
    log.underlying.error(t)(s"Failing stage [$id]")
    failStage(t)
  }

  private def addInflight(inflightId : String, ackCtxt : T, state : AckState) : Unit = {
    inflightMap += inflightId -> (ackCtxt -> state)
    log.underlying.debug(s"Inflight message count for [$id] is [${inflightMap.size}]")
  }

  private def removeInflight(inflightId : String) : Unit = {
    inflightMap -= inflightId
    log.underlying.debug(s"Inflight message count for [$id] is [${inflightMap.size}]")
  }

  // A callback to immediately schedule the next poll
  protected val pollImmediately : AsyncCallback[Unit] = getAsyncCallback[Unit](_ => poll())

  // A callback to update the ack state for an inflight message
  protected val updateAckState : AsyncCallback[(String, AckState)] = getAsyncCallback[(String, AckState)]{ case (id, state) =>

    inflightMap.get(id) match {
      case Some((ctxt, _ )) =>
        log.logEnv(ctxt.envelope, LogLevel.Debug, s"Updating state of [${ctxt.envelope.id}] for [$id] to [$state]")
        inflightMap.put(id, (ctxt, state))
        state match {
          case AckState.Acknowledged => acknowledged(ctxt)
          case AckState.Denied => denied(ctxt)
          case _ =>
        }
      case None =>
        log.underlying.debug(s"AckContext [$id] no longer inflight - perhaps it has timed out ?")
    }
  }

  private def acknowledged(ackCtxt : T) : Unit = {
    ackCtxt.acknowledge()
    log.logEnv(ackCtxt.envelope, LogLevel.Debug, s"Flow envelope [${ackCtxt.envelope.id}] has been acknowledged in [$id]")
    // Then we clear the message from the inflight map
    removeInflight(ackCtxt.inflightId)
    // If the poll timer is active we actually have executed a poll recently with no result
    if (!isTimerActive(Poll)) {
      pollImmediately.invoke(())
    }
  }

  override def postStop() : Unit = {

    val p : Map[String, T] = pending()
    if (p.nonEmpty) {
      log.underlying.debug(s"[$id] has [${p.size}] envelopes still in inflight while stopping")
    }

    // perform any implementation specific logic to roll back pending envelopes
    p.values.foreach(_.deny())
  }

  // this will be called whenever an inflight message has been denied
  private def denied(ackCtxt: T) : Unit = {
    ackCtxt.deny()
    log.logEnv(ackCtxt.envelope, LogLevel.Debug, s"Flow Envelope [${ackCtxt.envelope.id}] has been denied in [$id]")
    // we need to clean up the inflight map
    removeInflight(ackCtxt.inflightId)
    // If the poll timer is active we actually have executed a poll recently with no result
    if (!isTimerActive(Poll)) {
      pollImmediately.invoke(())
    }
  }

  // this will be called whenever the acknowledgement for an inflight
  // message has timed out. Per default this will be delegated to the
  // denied() handler
  protected def ackTimedOut(ackCtxt : T): Unit = denied(ackCtxt)

  protected def determineNextSlot(slotList : List[String]) : Option[String] = {
    lastUsedSlot = slotList.filter { id => !inflightMap.keys.exists(_ == id) } match {
      case Nil => None
      case head :: Nil => Some(head)
      case l =>
        Some(lastUsedSlot.flatMap{ lu => l.find{i => i > lu} }.getOrElse(l.head))
    }

    lastUsedSlot
  }

  protected def freeInflightSlot() : Option[String] = {
    determineNextSlot(inflightSlots)
  }

  /* Concrete implementations must implement this method to realize the technical
     poll from the external system */
  protected def doPerformPoll(id : String, ackHandler : AcknowledgeHandler) : Try[Option[T]]

  /* Perform a poll of the external system within the context of a free inflight slot */
  private def performPoll(id : String) : Unit = Try {
    // make sure, the outlet has been pulled. If that is not the case,
    // the out handler will be called eventually, triggering another
    // poll()
    if (isAvailable(out) && !isTimerActive(Poll)) {

      val ackHandler : AcknowledgeHandler = new AcknowledgeHandler {
        override def acknowledge() : Try[Unit] = Try {
          updateAckState.invoke((id, AckState.Acknowledged))
        }

        override def deny() : Try[Unit] = Try {
          updateAckState.invoke((id, AckState.Denied))
        }
      }

      log.underlying.trace(s"Performing poll for [$id]")
      doPerformPoll(id, ackHandler) match {
        case Success(None) =>

          nextPoll() match {
            case None => pollImmediately.invoke(())
            case Some(d) => if (!isTimerActive(Poll)) {
              log.underlying.trace(s"Scheduling next poll for [$id] in [$d]")
              scheduleOnce(Poll, d)
              ()
            }
          }

        case Success(Some(ackCtxt)) =>
          // add the context to the inflight messages
          log.logEnv(ackCtxt.envelope, LogLevel.Debug, s"Received [${ackCtxt.envelope.flowMessage}] in [$id]")
          addInflight(id, ackCtxt, AckState.Pending)
          // push the envelope to the outlet
          if (autoAcknowledge) {
            log.logEnv(ackCtxt.envelope, LogLevel.Debug, s"Auto Acknowledging [${ackCtxt.envelope.id}] in [$id]")
            acknowledged(ackCtxt)
            push(out, ackCtxt.envelope.withRequiresAcknowledge(false).withAckHandler(None))
          } else {
            addInflight(id, ackCtxt, AckState.Pending)
            push(out, ackCtxt.envelope)
          }

        case Failure(t) =>
          log.underlying.warn(s"Failed to poll for new message in [$id]")
          log.underlying.trace(t)(s"Failed to poll for new message in [$id]")
          failStage(t)
      }
    }
  }

  protected def pending() : Map[String, T] =
    inflightMap.filter { case (_, (_, state)) => state == AckState.Pending }.toMap.view.mapValues(_._1).toMap

  override protected def onTimer(timerKey : Any) : Unit = {
    timerKey match {
      case CheckAck =>

        val timedoutAcks : Map[String, T] =
          pending().filter {
            case (_, ctxt) =>
              System.currentTimeMillis() - ctxt.created > ackTimeout.toMillis
          }

        timedoutAcks.values.foreach { ctxt =>
          log.logEnv(ctxt.envelope, LogLevel.Warn, s"Acknowledge for [${ctxt.envelope}] has timed out in [${ctxt.inflightId}]")
          ackTimedOut(ctxt)
        }

      case Poll =>
        log.underlying.trace(s"Received scheduled poll event")
        poll()
    }
  }

  /**
   * Poll the external system whether a new message is available.
   * If a message can be polled without any exceptions, the resulting envelope
   * will be wrapped in an AcknowledgeContext and returned.
   * If no message is available, the result will be Success(None)
   * *
   * Any exception while polling for a message will deny all remaining inflight
   * messages and then fail the stage.
   */

  protected def poll() : Unit = {
    // select a free inflight slot and trigger to poll and process the next inbound
    // message
    freeInflightSlot().foreach(performPoll)
  }

  /*
     Finally set the handler for the outlet.
  */

  setHandler(out, new OutHandler() {
    override def onPull() : Unit = {
      if (!isTimerActive(Poll)) {
        poll()
      }
    }
  })
}
