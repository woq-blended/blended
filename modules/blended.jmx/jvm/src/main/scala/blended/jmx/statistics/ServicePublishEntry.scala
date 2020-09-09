package blended.jmx.statistics

import java.text.SimpleDateFormat
import java.util.Date

import blended.jmx.JmxObjectName
import blended.jmx.NamingStrategy

case class ServicePublishEntry (
  component : String,
  subComponents : Map[String, String],
  successCount : Long,
  totalSuccessMsec : Long,
  avgSuccessMsec : Double,
  failedCount : Long,
  totalFailedMsec : Long,
  avgFailedMsec : Double,
  inflight : Long,
  lastFailed : String
) {


  override def toString: String = {
    val ns : NamingStrategy = new ServiceNamingStrategy()
    s"${getClass().getSimpleName()}(objectName='${ns.objectName(this)}', successCount=$successCount, failedCount=$failedCount, inflight=$inflight, lastFailed=$lastFailed)"
  }
}

object ServicePublishEntry {

  private val sdf : SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS")

  def create(e : Entry) : ServicePublishEntry = {

    ServicePublishEntry(
      component = e.component,
      subComponents = e.subComponents,
      successCount = e.success.count,
      totalSuccessMsec = e.success.totalMsec,
      avgSuccessMsec = e.success.avg,
      failedCount = e.failed.count,
      totalFailedMsec = e.failed.totalMsec,
      avgFailedMsec = e.failed.avg,
      inflight = e.inflight,
      lastFailed = if (e.lastFailed == -1L) {
        ""
      } else {
        sdf.format(new Date(e.lastFailed))
      }
    )
  }
}

class ServiceNamingStrategy extends NamingStrategy {

  override val objectName: PartialFunction[Any,JmxObjectName] = {
    case e : ServicePublishEntry => JmxObjectName(properties =
      Map("component" -> e.component) ++ e.subComponents
    )
  }
}
