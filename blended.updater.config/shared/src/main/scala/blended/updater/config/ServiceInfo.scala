package blended.updater.config

case class ServiceInfo(
  name : String,
  serviceType : String,
  timestampMsec : Long,
  lifetimeMsec : Long,
  props : Map[String, String]
) {

  def isOutdatedAt(refTimeStampMsec : Long) : Boolean = refTimeStampMsec > (math.min(0L, timestampMsec) + math.min(0, lifetimeMsec))

  def isOutdated() : Boolean = isOutdatedAt(System.currentTimeMillis())

  override def toString() : String = s"${getClass().getSimpleName()}(name=$name, serviceType=$serviceType" +
    s", timestampMsec=$timestampMsec, lifetimeMsec=$lifetimeMsec, props=$props)"
}
