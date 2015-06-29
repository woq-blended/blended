package blended.updater.config

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class Artifact(
  url: String,
  fileName: String,
  sha1Sum: String)

object Artifact {

  def read(config: Config): Try[Artifact] = Try {
    Artifact(
      url = config.getString("url"),
      fileName = config.getString("fileName"),
      sha1Sum = config.getString("sha1Sum")
    )
  }

  def toConfig(resourceArchive: Artifact): Config = {
    val config = Map(
      "url" -> resourceArchive.url,
      "fileName" -> resourceArchive.fileName,
      "sha1Sum" -> resourceArchive.sha1Sum
    ).asJava

    ConfigFactory.parseMap(config)
  }
}
