package blended.updater.config

case class ContainerInfo(
  containerId: String,
  properties: Map[String, String],
  serviceInfos: List[ServiceInfo],
  profiles: List[Profile]
) {

  override def toString(): String = s"${getClass().getSimpleName()}(containerId=${containerId},properties=${properties},serviceInfos=${serviceInfos},profiles=${profiles})"

}
