package blended.updater.config

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.collection.immutable._

case class FragmentConfig(
  name: String,
  version: String,
  url: Option[String],
  bundles: Seq[BundleConfig],
  fragments: Seq[FragmentConfig])

object FragmentConfig {
  def apply(name: String,
    version: String,
    url: String = null,
    bundles: Seq[BundleConfig] = null,
    fragments: Seq[FragmentConfig] = null): FragmentConfig = {
    FragmentConfig(
      name = name,
      version = version,
      url = Option(url),
      bundles = Option(bundles).getOrElse(Seq()),
      fragments = Option(fragments).getOrElse(Seq())
    )
  }

  def read(config: Config): Try[FragmentConfig] = Try {
    FragmentConfig(
      name = config.getString("name"),
      version = config.getString("version"),
      url =
        if (config.hasPath("url")) Option(config.getString("path")) else None,
      bundles =
        if (config.hasPath("bundles")) {
          config.getObjectList("bundles").asScala.map { bc => BundleConfig.read(bc.toConfig()).get }.toList
        } else Nil,
      fragments =
        if (config.hasPath("fragments")) {
          config.getObjectList("fragments").asScala.map { f => FragmentConfig.read(f.toConfig()).get }.toList
        } else Nil
    )
  }
  def toConfig(fragmentConfig: FragmentConfig): Config = {
    val config = (Map(
      "name" -> fragmentConfig.name,
      "version" -> fragmentConfig.version
    ) ++
      fragmentConfig.url.map(url => Map("url" -> url)).getOrElse(Map()) ++
      {
        if (fragmentConfig.fragments.isEmpty) Map()
        else Map("fragments" -> fragmentConfig.fragments.map(FragmentConfig.toConfig).map(_.root().unwrapped()).asJava)
      } ++
      {
        if (fragmentConfig.bundles.isEmpty) Map()
        else Map("bundles" -> fragmentConfig.bundles.map(BundleConfig.toConfig).map(_.root().unwrapped()).asJava)
      }
    ).asJava
    ConfigFactory.parseMap(config)
  }
}
