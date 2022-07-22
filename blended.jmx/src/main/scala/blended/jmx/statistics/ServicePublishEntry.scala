package blended.jmx.statistics

import blended.jmx.{JmxObjectName, NamingStrategy}

case class ServicePublishEntry(
  component: String,
  subComponents: Map[String, String],
  successCount: Long,
  totalSuccessMsec: Long,
  avgSuccessMsec: Double,
  failedCount: Long,
  totalFailedMsec: Long,
  avgFailedMsec: Double,
  inflight: Long,
  lastFailed: String
) {

  override def toString: String = {
    val ns: NamingStrategy = new ServiceNamingStrategy()
    s"${getClass().getSimpleName()}(objectName='${ns.objectName(this)}', successCount=$successCount, failedCount=$failedCount, inflight=$inflight, lastFailed=$lastFailed)"
  }
}

class ServiceNamingStrategy extends NamingStrategy {

  override val objectName: PartialFunction[Any, JmxObjectName] = {
    case e: ServicePublishEntry =>
      JmxObjectName(properties =
        Map("component" -> e.component) ++ e.subComponents
      )
  }
}
