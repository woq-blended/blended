package blended.updater.config

import java.io.File
import java.net.URL

import scala.collection.immutable
import scala.collection.immutable.Map
import scala.util.Try

case class RuntimeConfig(
  name: String,
  version: String,
  bundles: immutable.Seq[BundleConfig] = immutable.Seq(),
  startLevel: Int,
  defaultStartLevel: Int,
  properties: Map[String, String] = Map(),
  frameworkProperties: Map[String, String] = Map(),
  systemProperties: Map[String, String] = Map(),
  features: immutable.Seq[FeatureRef] = immutable.Seq(),
  resources: immutable.Seq[Artifact] = immutable.Seq(),
  resolvedFeatures: immutable.Seq[FeatureConfig] = immutable.Seq()) {

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},bundles=${bundles}" +
    s",startLevel=${startLevel},defaultStartLevel=${defaultStartLevel},properties=${properties},frameworkProperties=${frameworkProperties}" +
    s",systemProperties=${systemProperties},features=${features},resources=${resources},resolvedFeatures=${resolvedFeatures})"

  def mvnBaseUrl: Option[String] = properties.get(RuntimeConfig.Properties.MVN_REPO)

  def resolveBundleUrl(url: String): Try[String] = RuntimeConfig.resolveBundleUrl(url, mvnBaseUrl)

  def resolveFileName(url: String): Try[String] = RuntimeConfig.resolveFileName(url)

  // TODO: Review this for JavaScript
  def baseDir(profileBaseDir: File): File = new File(profileBaseDir, s"${name}/${version}")

  //    def localRuntimeConfig(baseDir: File): LocalRuntimeConfig = LocalRuntimeConfig(runtimeConfig = this, baseDir = baseDir)

  /**
    * Try to create a [ResolvedRuntimeConfig]. This does not fetch missing [FeatureConfig]s.
    *
    * @see [FeatureResolver] for a way to resolve missing features.
    */
  def resolve(features: immutable.Seq[FeatureConfig] = immutable.Seq()): Try[ResolvedRuntimeConfig] = Try {
    ResolvedRuntimeConfig(this, features.to[immutable.Seq])
  }
}

object RuntimeConfig
  extends ((String, String, immutable.Seq[BundleConfig], Int, Int, Map[String, String], Map[String, String], Map[String, String], immutable.Seq[FeatureRef], immutable.Seq[Artifact], immutable.Seq[FeatureConfig]) => RuntimeConfig) {

  val MvnPrefix = "mvn:"

  object Properties {
    val PROFILES_BASE_DIR = "blended.updater.profiles.basedir"
    val PROFILE_DIR = "blended.updater.profile.dir"
    val PROFILE_NAME = "blended.updater.profile.name"
    val PROFILE_VERSION = "blended.updater.profile.version"
    /**
      * selected overlays, format: name:version,name:verion
      */
    val OVERLAYS = "blended.updater.profile.overlays"
    val PROFILE_LOOKUP_FILE = "blended.updater.profile.lookup.file"
    val MVN_REPO = "blended.updater.mvn.url"
    /** A properties file relative to the profile dir */
    val PROFILE_PROPERTY_FILE = "blended.updater.profile.properties.file"
    /** Comma separated list of property providers */
    val PROFILE_PROPERTY_PROVIDERS = "blended.updater.profile.properties.providers"
    /** Comma separated list of properties required to be in the properties file */
    val PROFILE_PROPERTY_KEYS = "blended.updater.profile.properties.keys"
  }

  def resolveBundleUrl(url: String, mvnBaseUrl: Option[String] = None): Try[String] = Try {
    if (url.startsWith(MvnPrefix)) {
      mvnBaseUrl match {
        case Some(base) => MvnGav.parse(url.substring(MvnPrefix.size)).get.toUrl(base)
        case None => sys.error("No repository defined to resolve url: " + url)
      }
    } else url
  }

  def resolveFileName(url: String): Try[String] = Try {
    val resolvedUrl = if (url.startsWith(MvnPrefix)) {
      MvnGav.parse(url.substring(MvnPrefix.size)).get.toUrl("file:///")
    } else url
    val path = new URL(resolvedUrl).getPath()
    path.split("[/]").filter(!_.isEmpty()).reverse.headOption.getOrElse(path)
  }

}

