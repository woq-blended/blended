package blended.updater

import scala.collection.immutable.Seq
import com.typesafe.config.Config

case class BundleConfig(
  //  symbolicName: String,
  //  version: String,
  url: String,
  jarName: String,
  sha1Sum: String,
  start: Boolean,
  startLevel: Option[Int])

case class RuntimeConfig(
  name: String,
  version: String,
  framework: BundleConfig,
  bundles: Seq[BundleConfig],
  startLevel: Int,
  defaultStartLevel: Int,
  frameworkProperties: Map[String, String],
  systemProperties: Map[String, String])

object RuntimeConfig {

  def read(config: Config): RuntimeConfig = {
    ???
  }

}