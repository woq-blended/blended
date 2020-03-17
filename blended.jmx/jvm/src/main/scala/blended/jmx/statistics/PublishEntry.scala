package blended.jmx.statistics

import java.text.SimpleDateFormat
import java.util.Date

import blended.jmx.JmxObjectName
import javax.management.ObjectName

case class PublishEntry private (
  name : ObjectName,
  successCount : Long,
  totalSuccessMsec : Long,
  avgSuccessMsec : Double,
  failedCount : Long,
  totalFailedMsec : Long,
  avgFailedMsec : Double,
  inflight : Long,
  lastFailed : String
) {
  override def toString: String =
    s"${getClass().getSimpleName()}(name='${name}', successCount=$successCount, failedCount=$failedCount, inflight=$inflight, lastFailed=$lastFailed)"

}

object PublishEntry {

  private val sdf : SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS")

  def create(e : Entry) : PublishEntry = {

    val objectName : ObjectName = new ObjectName(JmxObjectName(properties =
      Map("component" -> e.component) ++ e.subComponents
    ).objectName)

    PublishEntry(
      name = objectName,
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
