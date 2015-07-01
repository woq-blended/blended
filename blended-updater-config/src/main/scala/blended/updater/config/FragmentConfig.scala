package blended.updater.config

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class FragmentConfig(
  name: String,
  version: String,
  bundles: Seq[BundleConfig])

object FragmentConfig {
  def read(config: Config): Try[FragmentConfig] = Try {
    FragmentConfig(
      name = config.getString("name"),
      version = config.getString("version"),
      bundles =
        if (config.hasPath("bundles")) {
          config.getObjectList("bundles").asScala.map { bc => BundleConfig.read(bc.toConfig()).get }.toList
        } else Nil
    )
  }
  def toConfig(fragmentConfig: FragmentConfig): Config = {
    val config = Map(
      "name" -> fragmentConfig.name,
      "version" -> fragmentConfig.version,
      "bundles" -> fragmentConfig.bundles.map(BundleConfig.toConfig).map(_.root().unwrapped()).asJava
    ).asJava
    ConfigFactory.parseMap(config)
  }
}
