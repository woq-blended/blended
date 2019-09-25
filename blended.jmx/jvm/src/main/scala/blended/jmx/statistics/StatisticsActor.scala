package blended.jmx.statistics

import scala.util.control.NonFatal

import akka.actor.{Actor, Props}
import blended.jmx.OpenMBeanExporter
import blended.util.logging.Logger
import javax.management.ObjectName

class StatisticsActor(mbeanExporter: OpenMBeanExporter) extends Actor {
  import StatisticsActor._

  private[this] val log = Logger[this.type]

  // BEGIN actor state
  private[this] var collectedData: Map[String, Entry] = Map()
  // END actor state

  override def preStart(): Unit = {
    super.preStart()
    // register event handler
    log.debug(s"Subscribing self [${self}] to StatisticData from system.eventStream")
    context.system.eventStream.subscribe(self, classOf[StatisticData])
  }

  override def receive: Receive = {
    case sd: StatisticData => handleStatisticsData(sd)
  }

  def updateJmxRegistration(entry: Entry) = {
    log.debug(s"Exporting/updating JMX entry [${entry.name}]")
    mbeanExporter.export(entry, new ObjectName(entry.name), replaceExisting = true).recover {
      case NonFatal(e) =>
        log.warn(e)(s"Could not register mbean with name [${entry.name}]")
    }
  }

  def handleStatisticsData(value: StatisticData) = value match {
    case newData @ StatisticData(name, id, newState, timeStamp) =>
      val entry = collectedData.getOrElse(name, Entry(name))
      val existing = entry.unfinishedData.get(id)
      val updatedEntry = (newState, existing) match {

        case (ServiceState.Started, None) => entry.copy(
          unfinishedData = entry.unfinishedData + (id -> newData)
        )
        case (ServiceState.Started, Some(e)) =>
          // we got a second started event for the same entry
          if (newData != e) {
            log.warn(s"Got a second started event: [${newData}] but we already have recorded one: [${e}]. Ignoring the new one.")
          }
          entry

        case (ServiceState.Completed, Some(e)) => entry.copy(
          successCount = entry.successCount + 1,
          aggregateSuccessMsec = entry.aggregateSuccessMsec + math.max(0, timeStamp - e.timeStamp),
          unfinishedData = entry.unfinishedData - id
        )
        case (ServiceState.Completed, None) =>
          log.warn(s"Got a completed event without a previous started event: [${newData}]")
          entry.copy(
            successCount = entry.successCount + 1
          )

        case (ServiceState.Failed, Some(e)) => entry.copy(
          failedCount = entry.failedCount + 1,
          aggregateFailedMsec = entry.aggregateFailedMsec + math.max(0, timeStamp - e.timeStamp),
          unfinishedData = entry.unfinishedData - id,
          lastFailed = timeStamp
        )
        case (ServiceState.Failed, None) =>
          log.warn(s"Got a failed event without a previous started event: [${newData}]")
          entry.copy(
            failedCount = entry.failedCount + 1,
            lastFailed = timeStamp
          )
      }
      collectedData += name -> updatedEntry
      if (entry != updatedEntry) {
        updateJmxRegistration(updatedEntry)
      }
  }

}

object StatisticsActor {

  sealed trait ServiceState
  object ServiceState {
    final case object Started extends ServiceState
    final case object Completed extends ServiceState
    final case object Failed extends ServiceState
  }

  case class StatisticData(
    name: String,
    id: String,
    state: ServiceState,
    timeStamp: Long = System.currentTimeMillis()
  )

  case class Entry(
    name: String,
    successCount: Long = 0,
    aggregateSuccessMsec: Long = 0,
    failedCount: Long = 0,
    aggregateFailedMsec: Long = 0,
    unfinishedData: Map[String, StatisticData] = Map(),
    lastFailed: Long = -1L
  )

  def props(mbeanExporter: OpenMBeanExporter): Props = Props(new StatisticsActor(mbeanExporter))
}

