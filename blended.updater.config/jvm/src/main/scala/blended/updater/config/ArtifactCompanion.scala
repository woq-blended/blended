package blended.updater.config

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._
import scala.util.Try

object ArtifactCompanion {

  def read(config : Config) : Try[Artifact] = Try {
    Artifact(
      url = config.getString("url"),
      fileName = if (config.hasPath("fileName")) Option(config.getString("fileName")) else None,
      sha1Sum = if (config.hasPath("sha1Sum")) Option(config.getString("sha1Sum")) else None
    )
  }

  def toConfig(artifact : Artifact) : Config = {
    val config = (
      Map("url" -> artifact.url) ++
      artifact.fileName.map(n => Map("fileName" -> n)).getOrElse(Map()) ++
      artifact.sha1Sum.map(s => Map("sha1Sum" -> s)).getOrElse(Map())
    ).asJava

    ConfigFactory.parseMap(config)
  }
}
