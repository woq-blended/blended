package blended.jmx.statistics

import scala.util.control.NonFatal
import akka.actor.{Actor, Props}
import blended.jmx.{JmxObjectName, OpenMBeanExporter}
import blended.util.logging.Logger
import javax.management.ObjectName

import scala.util.Try

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

  private def updateJmxRegistration(entry: Entry) : Try[Unit] = {
    val toPublish : PublishEntry = PublishEntry(entry)
    log.debug(s"Exporting/updating JMX entry [${toPublish.name}]")
    mbeanExporter.export(toPublish, toPublish.name, replaceExisting = true).recover {
      case NonFatal(e) =>
        log.warn(e)(s"Could not register mbean with name [${toPublish.name}]")
    }
  }

  private val datakey : StatisticData => String = sd => sd.component + "-" + sd.subComponent

  private def handleStatisticsData(value: StatisticData) : Unit = value match {
    case newData @ StatisticData(component, subComponent, id, newState, timeStamp) =>

      log.debug(s"Processing statistical data [$newData]")

      val entry : Entry = collectedData.getOrElse(datakey(newData), Entry(component, subComponent))

      val existing : Option[StatisticData] = entry.unfinishedData.get(id)

      val updatedEntry : Entry = (newState, existing) match {

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
      collectedData += datakey(newData) -> updatedEntry
      if (entry != updatedEntry) {
        updateJmxRegistration(updatedEntry)
      }
  }

}

object StatisticsActor {

  case class Entry(
    component : String,
    subComponent : Option[String],
    successCount: Long = 0,
    aggregateSuccessMsec: Long = 0,
    failedCount: Long = 0,
    aggregateFailedMsec: Long = 0,
    unfinishedData: Map[String, StatisticData] = Map(),
    lastFailed: Long = -1L
  )

  object PublishEntry {
    def apply(e : Entry) : PublishEntry = {

      val objectName : ObjectName = new ObjectName(JmxObjectName(properties =
        Map("component" -> e.component) ++
        e.subComponent.map(s => Map("subcomponent" -> s)).getOrElse(Map.empty)
      ).objectName)

      PublishEntry(
        objectName, e.successCount, e.aggregateSuccessMsec, e.failedCount, e.aggregateFailedMsec, e.unfinishedData.size, e.lastFailed
      )
    }
  }

  case class PublishEntry(
    name : ObjectName,
    successCount : Long,
    aggregateSuccessMsec : Long = 0,
    failedCount : Long,
    aggregateFailedMsec : Long,
    inflight : Long,
    lastFailed : Long
  )

  def props(mbeanExporter: OpenMBeanExporter): Props = Props(new StatisticsActor(mbeanExporter))
}

