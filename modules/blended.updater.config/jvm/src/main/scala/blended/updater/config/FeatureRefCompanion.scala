package blended.updater.config

import com.typesafe.config.{Config, ConfigFactory}
import scala.jdk.CollectionConverters._
import scala.util.Try
import com.typesafe.config.ConfigValueFactory
import blended.util.config.Implicits._

object FeatureRefCompanion {

  def toConfig(feature : FeatureRef) : Config = {

    val cfg : Config = ConfigFactory.empty()
      .withValue("url", ConfigValueFactory.fromAnyRef(feature.url))
      .withValue("names", ConfigValueFactory.fromIterable(feature.names.asJava))

    cfg
  }

  def fromConfig(config : Config) : Try[FeatureRef] = Try {
    FeatureRef(
      url = config.getString("url"),
      names = config.getStringList("names", List.empty).toList
    )
  }
}
