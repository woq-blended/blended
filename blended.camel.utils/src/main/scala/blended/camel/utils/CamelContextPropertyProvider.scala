package blended.camel.utils

import blended.container.context.api.ContainerIdentifierService

import scala.collection.JavaConverters._

trait CamelContextPropertyProvider {
  def contextProperties : Map[String, String] = Map.empty
}

trait IdServiceCamelContextPropertyProvider extends CamelContextPropertyProvider {

  def idService : ContainerIdentifierService

  val PROPERTIES_KEY = "blended.camel.context.properties"

  override def contextProperties : Map[String, String] = {
    val cfg = idService.containerContext.getContainerConfig()

    if (cfg.hasPath(PROPERTIES_KEY)) {
      val props = cfg.getConfig(PROPERTIES_KEY)

      props.entrySet().asScala.map { entry =>
        entry.getKey() -> props.getString(entry.getKey())
      }.toMap
    } else {
      Map.empty
    }
  }
}

