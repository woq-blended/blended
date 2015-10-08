package blended.updater.config

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.collection.immutable._

case class FeatureConfig(
    name: String,
    version: String,
    url: Option[String],
    bundles: Seq[BundleConfig],
    features: Seq[FeatureConfig]) {

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},url=${url},bundles=${bundles},features=${features})"

  def allBundles: Seq[BundleConfig] = bundles ++ features.flatMap(_.allBundles)

}

object FeatureConfig {
  def apply(name: String,
    version: String,
    url: String = null,
    bundles: Seq[BundleConfig] = null,
    features: Seq[FeatureConfig] = null): FeatureConfig = {
    FeatureConfig(
      name = name,
      version = version,
      url = Option(url),
      bundles = Option(bundles).getOrElse(Seq()),
      features = Option(features).getOrElse(Seq())
    )
  }

  def read(config: Config): Try[FeatureConfig] = Try {
    FeatureConfig(
      name = config.getString("name"),
      version = config.getString("version"),
      url =
        if (config.hasPath("url")) Option(config.getString("path")) else None,
      bundles =
        if (config.hasPath("bundles")) {
          config.getObjectList("bundles").asScala.map { bc => BundleConfig.read(bc.toConfig()).get }.toList
        } else Nil,
      features =
        if (config.hasPath("features")) {
          config.getObjectList("features").asScala.map { f => FeatureConfig.read(f.toConfig()).get }.toList
        } else Nil
    )
  }
  def toConfig(featureConfig: FeatureConfig): Config = {
    val config = (Map(
      "name" -> featureConfig.name,
      "version" -> featureConfig.version
    ) ++
      featureConfig.url.map(url => Map("url" -> url)).getOrElse(Map()) ++
      {
        if (featureConfig.features.isEmpty) Map()
        else Map("features" -> featureConfig.features.map(FeatureConfig.toConfig).map(_.root().unwrapped()).asJava)
      } ++
      {
        if (featureConfig.bundles.isEmpty) Map()
        else Map("bundles" -> featureConfig.bundles.map(BundleConfig.toConfig).map(_.root().unwrapped()).asJava)
      }
    ).asJava
    ConfigFactory.parseMap(config)
  }
}
