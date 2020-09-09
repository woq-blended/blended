package blended.jmx.statistics

import akka.actor.Actor
import blended.util.logging.Logger
import akka.actor.ActorSystem
import blended.jmx.ProductMBeanManager
import akka.actor.Props

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
  invocations : Map[String, ServiceInvocationStarted] = Map.empty,
  entries : Map[String, Entry] = Map.empty,
  mbeanMgr : ProductMBeanManager
)(implicit system: ActorSystem) {

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
      val toPublish : ServicePublishEntry = ServicePublishEntry.create(e)
      log.trace(s"Exporting/updating JMX entry : [$toPublish]")
      mbeanMgr.updateMBean(toPublish)
    }

    newState
  }

  private def complete(evt : ServiceInvocationEvent) : (StatisticsState, Option[Entry]) = {
    log.trace(s"Recording Service invocation event [$evt]")
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
        log.trace(s"Recording Service invocation start [$evt]")
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

object ServiceStatisticsActor {
  def props(mbeanMgr : ProductMBeanManager) : Props = Props(new ServiceStatisticsActor(mbeanMgr))
}

class ServiceStatisticsActor(mbeanMgr : ProductMBeanManager) extends Actor {

  private[this] val log = Logger[this.type]

  override def preStart(): Unit = {
    super.preStart()
    // register event handler
    log.debug(s"Subscribing self [${self}] to ServiceInvocationEvents from system.eventStream")
    context.system.eventStream.subscribe(self, classOf[ServiceInvocationEvent])
    context.become(working(StatisticsState(mbeanMgr = mbeanMgr)(context.system)))
  }

  override def receive: Receive = Actor.emptyBehavior

  private def working(statsData : StatisticsState) : Receive = {
    case evt : ServiceInvocationEvent =>
      val newState : StatisticsState = statsData.update(evt)
      log.debug(s"new statistics state is [$newState]")
      context.become(working(newState))
  }
}
