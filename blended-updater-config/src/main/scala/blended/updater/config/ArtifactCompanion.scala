package blended.updater.config

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object ArtifactCompanion {

  def read(config: Config): Try[Artifact] = Try {
    Artifact(
      url = config.getString("url"),
      fileName = if (config.hasPath("fileName")) Option(config.getString("fileName")) else None,
      sha1Sum = if (config.hasPath("sha1Sum")) Option(config.getString("sha1Sum")) else None
    )
  }

  def toConfig(resourceArchive: Artifact): Config = {
    val config = (
      Map("url" -> resourceArchive.url) ++
      resourceArchive.fileName.map(n => Map("fileName" -> n)).getOrElse(Map()) ++
      resourceArchive.sha1Sum.map(s => Map("sha1Sum" -> s)).getOrElse(Map())
    ).asJava

    ConfigFactory.parseMap(config)
  }
}
