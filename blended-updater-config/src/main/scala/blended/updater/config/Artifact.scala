package blended.updater.config

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class Artifact(
    url: String,
    fileName: Option[String],
    sha1Sum: Option[String]) {

  override def toString(): String = s"${getClass().getSimpleName()}(url=${url},fileName=${fileName},sha1Sum=${sha1Sum})"

}

object Artifact extends ((String, Option[String], Option[String]) => Artifact) {
  def apply(url: String,
    fileName: String = null,
    sha1Sum: String = null): Artifact = {
    Artifact(
      url = url,
      fileName = Option(fileName),
      sha1Sum = Option(sha1Sum)
    )
  }

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
