package blended.jmx.statistics

import scala.util.control.NonFatal
import akka.actor.{Actor, Props}
import blended.jmx.OpenMBeanExporter
import blended.util.logging.Logger

case class Accumulator(
  count : Long = 0,
  totalMsec : Long = 0,
  minMsec : Long = Long.MaxValue,
  maxMsec : Long = Long.MinValue
) {
  def record(msec : Long) : Accumulator = {
    copy(
      count = count + 1,
      totalMsec = totalMsec + msec,
      minMsec = Math.min(minMsec, msec),
      maxMsec = Math.max(maxMsec, msec)
    )
  }

  val avg : Double = if (count == 0) {
    0d
  } else {
    totalMsec.toDouble / count
  }
}

case class Entry(
  component : String,
  subComponents : Map[String, String],
  success : Accumulator = Accumulator(),
  failed : Accumulator = Accumulator(),
  lastFailed: Long = -1L,
  inflight : Long = 0L
) {

  private def duration(started : ServiceInvocationEvent, completed : ServiceInvocationEvent) : Long =
    Math.max(0, completed.timestamp - started.timestamp)

  def update(evt : ServiceInvocationEvent, started : ServiceInvocationStarted) : Entry = evt match  {
    case _ : ServiceInvocationStarted =>
      copy(inflight = inflight + 1)
    case c : ServiceInvocationCompleted =>
      copy(success = success.record(duration(started, c)), inflight = inflight - 1)
    case f : ServiceInvocationFailed =>
      copy(failed = failed.record(duration(started, f)), lastFailed = System.currentTimeMillis(), inflight = inflight - 1)
  }
}

case class StatisticsState(
  mbeanExporter : OpenMBeanExporter,
  invocations : Map[String, ServiceInvocationStarted] = Map.empty,
  entries : Map[String, Entry] = Map.empty
) {

  private val log : Logger = Logger[StatisticsState]
  private val datakey : ServiceInvocationStarted => String = s => s"${s.component}-${s.subComponents.mkString(",")}"

  override def toString: String =
    s"${getClass().getSimpleName()}(${invocations.keys.mkString("(", ",", ")")}, ${entries.keys.mkString("(", ",", ")")})"

  def update(evt : ServiceInvocationEvent) : StatisticsState = {
    val (newState, entry) : (StatisticsState, Option[Entry]) = evt match {
      case started : ServiceInvocationStarted => invocationStarted(started)
      case completed : ServiceInvocationEvent => complete(completed)
    }

    entry.foreach { e =>
      val toPublish : PublishEntry = PublishEntry.create(e)
      log.debug(s"Exporting/updating JMX entry [${toPublish.name}] : [$toPublish]")
      mbeanExporter.export(toPublish, toPublish.name, replaceExisting = true).recover {
        case NonFatal(e) =>
          log.warn(e)(s"Could not register mbean with name [${toPublish.name}]")
      }
    }

    newState
  }

  private def complete(evt : ServiceInvocationEvent) : (StatisticsState, Option[Entry]) = {
    log.debug(s"Recording Service invocation event [$evt]")
    invocations.get(evt.id) match {
      case None =>
        log.debug(s"No active service invocation found for [${evt.id}]")
        (this, None)
      case Some(started) =>
        val key : String = datakey(started)
        entries.get(key) match {
          case None =>
            log.warn(s"No statistics entry found for [$key]")
            (this, None)
          case Some(e) =>
            evt match {
              case _ : ServiceInvocationStarted =>
                log.warn(s"Unexpected event for updating completing entry [$key]")
                (this, None)
              case _ =>
                val newEntry : Entry = e.update(evt, started)
                (copy(
                  invocations = invocations - evt.id,
                  entries = entries + (key -> newEntry)
                ), Some(newEntry))
            }
        }
    }
  }

  private def invocationStarted(evt : ServiceInvocationStarted) : (StatisticsState, Option[Entry]) = {
    invocations.get(evt.id) match {
      case None =>
        log.debug(s"Recording Service invocation start [$evt]")
        val key : String = datakey(evt)
        val newEntry : Entry = entries.getOrElse(key, Entry(evt.component, evt.subComponents)).update(evt, evt)

        val newState : StatisticsState = copy(
          invocations = invocations + (evt.id -> evt),
          entries = entries + (key -> newEntry)
        )
        (newState, Some(newEntry))
      case Some(_) =>
        log.debug(s"Service with id [${evt.id}] has already been started")
        (this, None)
    }
  }
}

class StatisticsActor(mbeanExporter: OpenMBeanExporter) extends Actor {

  private[this] val log = Logger[this.type]

  override def preStart(): Unit = {
    super.preStart()
    // register event handler
    log.debug(s"Subscribing self [${self}] to ServiceInvocationEvents from system.eventStream")
    context.system.eventStream.subscribe(self, classOf[ServiceInvocationEvent])
    context.become(working(StatisticsState(mbeanExporter)))
  }

  override def receive: Receive = Actor.emptyBehavior

  private def working(statsData : StatisticsState) : Receive = {
    case evt : ServiceInvocationEvent =>
      val newState : StatisticsState = statsData.update(evt)
      log.debug(s"new statistics state is [$newState]")
      context.become(working(newState))
  }
}

object StatisticsActor {
  def props(mbeanExporter: OpenMBeanExporter): Props = Props(new StatisticsActor(mbeanExporter))
}

