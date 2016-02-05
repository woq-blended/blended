package blended.updater.config

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.immutable.Map
import scala.collection.immutable.Nil
import scala.collection.immutable.Seq
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class FeatureConfig(
    name: String,
    version: String,
    url: Option[String],
    bundles: Seq[BundleConfig],
    features: Seq[FeatureRef]) {

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},url=${url},bundles=${bundles},features=${features})"

  def featureRef: FeatureRef = FeatureRef(name = name, version = version, url = url)
}

object FeatureConfig extends ((String, String, Option[String], Seq[BundleConfig], Seq[FeatureRef]) => FeatureConfig) {
  def apply(name: String,
    version: String,
    url: String = null,
    bundles: Seq[BundleConfig] = null,
    features: Seq[FeatureRef] = null): FeatureConfig = {
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
          config.getObjectList("features").asScala.map { f => FeatureRef.fromConfig(f.toConfig()).get }.toList
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
        else Map("features" -> featureConfig.features.map(FeatureRef.toConfig).map(_.root().unwrapped()).asJava)
      } ++
      {
        if (featureConfig.bundles.isEmpty) Map()
        else Map("bundles" -> featureConfig.bundles.map(BundleConfig.toConfig).map(_.root().unwrapped()).asJava)
      }
    ).asJava
    ConfigFactory.parseMap(config)
  }
}
