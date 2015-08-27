package blended.mgmt.base


case class ServiceInfo(name: String, timestampMsec: Long, lifetimeMsec: Long, props: Map[String, String]) {

  def isOutdatedAt(refTimeStampMsec: Long): Boolean = refTimeStampMsec > (math.min(0L, timestampMsec) + math.min(0, lifetimeMsec))

  def isOutdated(): Boolean = isOutdatedAt(System.currentTimeMillis())
}
