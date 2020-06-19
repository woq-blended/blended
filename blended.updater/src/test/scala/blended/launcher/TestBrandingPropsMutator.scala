package blended.launcher
import java.util.Properties

object TestBrandingPropsMutator {

  def setBrandingProperties(properties: Map[String, String]) = {
    val props = new Properties()
    properties.foreach { case (k, v) => props.put(k, v) }
    BrandingProperties.setLastBrandingProperties(props)
  }

}
