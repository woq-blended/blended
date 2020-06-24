package blended.updater.config

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._
import scala.util.Try

object FeatureConfigCompanion {
  def apply(
    repoUrl : String,
    name : String,
    bundles : List[BundleConfig] = List.empty,
    features : List[FeatureRef] = List.empty
  ) : FeatureConfig = {
    FeatureConfig(
      repoUrl = repoUrl,
      name = name,
      bundles = Option(bundles).getOrElse(List.empty),
      features = Option(features).getOrElse(List.empty)
    )
  }

  def read(config : Config) : Try[FeatureConfig] = Try {
    FeatureConfig(
      repoUrl = config.getString("repoUrl"),
      name = config.getString("name"),
      bundles =
        if (config.hasPath("bundles")) {
          config.getObjectList("bundles").asScala.map { bc => BundleConfigCompanion.read(bc.toConfig()).get }.toList
        } else Nil,
      features =
        if (config.hasPath("features")) {
          config.getObjectList("features").asScala.map { f => FeatureRefCompanion.fromConfig(f.toConfig()).get }.toList
        } else Nil
    )
  }

  def toConfig(featureConfig : FeatureConfig) : Config = {
    val config = (Map(
      "repoUrl" -> featureConfig.repoUrl,
      "name" -> featureConfig.name
    ) ++
      {
        if (featureConfig.features.isEmpty) Map()
        else Map("features" -> featureConfig.features.map(FeatureRefCompanion.toConfig).map(_.root().unwrapped()).asJava)
      } ++
      {
        if (featureConfig.bundles.isEmpty) Map()
        else Map("bundles" -> featureConfig.bundles.map(BundleConfigCompanion.toConfig).map(_.root().unwrapped()).asJava)
      }
    ).asJava
    ConfigFactory.parseMap(config)
  }
}
