package blended.updater.config

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Formatter
import java.util.Properties

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.immutable
import scala.collection.immutable.Map
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions

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

  def read(config: Config): Try[RuntimeConfig] = Try {

    val optionals = ConfigFactory.parseResources(getClass(), "RuntimeConfig-optional.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    val reference = ConfigFactory.parseResources(getClass(), "RuntimeConfig-reference.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    config.withFallback(optionals).checkValid(reference)

    def configAsMap(key: String, default: Option[() => Map[String, String]] = None): Map[String, String] =
      if (default.isDefined && !config.hasPath(key)) {
        default.get.apply()
      } else {
        config.getConfig(key).entrySet().asScala.map {
          entry => entry.getKey() -> entry.getValue().unwrapped().asInstanceOf[String]
        }.toMap
      }

    val properties = configAsMap("properties", Some(() => Map()))

    RuntimeConfig(
      name = config.getString("name"),
      version = config.getString("version"),
      bundles =
        if (config.hasPath("bundles"))
          config.getObjectList("bundles").asScala.map { bc => BundleConfig.read(bc.toConfig()).get }.toList
        else immutable.Seq(),
      startLevel = config.getInt("startLevel"),
      defaultStartLevel = config.getInt("defaultStartLevel"),
      properties = properties,
      frameworkProperties = configAsMap("frameworkProperties", Some(() => Map())),
      systemProperties = configAsMap("systemProperties", Some(() => Map())),
      features =
        if (config.hasPath("features"))
          config.getObjectList("features").asScala.map { f =>
          FeatureRef.fromConfig(f.toConfig()).get
        }.toList
        else immutable.Seq(),
      resources =
        if (config.hasPath("resources"))
          config.getObjectList("resources").asScala.map(r => Artifact.read(r.toConfig()).get).toList
        else immutable.Seq(),
      resolvedFeatures =
        if (config.hasPath("resolvedFeatures"))
          config.getObjectList("resolvedFeatures").asScala.map(r => FeatureConfig.read(r.toConfig()).get).toList
        else immutable.Seq()
    )
  }

  def toConfig(runtimeConfig: RuntimeConfig): Config = {
    val config = Map(
      "name" -> runtimeConfig.name,
      "version" -> runtimeConfig.version,
      "bundles" -> runtimeConfig.bundles.map(BundleConfig.toConfig).map(_.root().unwrapped()).asJava,
      "startLevel" -> runtimeConfig.startLevel,
      "defaultStartLevel" -> runtimeConfig.defaultStartLevel,
      "properties" -> runtimeConfig.properties.asJava,
      "frameworkProperties" -> runtimeConfig.frameworkProperties.asJava,
      "systemProperties" -> runtimeConfig.systemProperties.asJava,
      "features" -> runtimeConfig.features.map(FeatureRef.toConfig).map(_.root().unwrapped()).asJava,
      "resources" -> runtimeConfig.resources.map(Artifact.toConfig).map(_.root().unwrapped()).asJava,
      "resolvedFeatures" -> runtimeConfig.resolvedFeatures.map(FeatureConfig.toConfig).map(_.root().unwrapped()).asJava
    ).asJava

    ConfigFactory.parseMap(config)
  }

  def bytesToString(digest: Array[Byte]): String = {
    import java.lang.StringBuilder
    val result = new StringBuilder(32);
    val f = new Formatter(result)
    digest.foreach(b => f.format("%02x", b.asInstanceOf[Object]))
    result.toString
  }

  def digestFile(file: File): Option[String] = {
    if (!file.exists()) None else {
      val sha1Stream = new DigestInputStream(new BufferedInputStream(new FileInputStream(file)), MessageDigest.getInstance("SHA"))
      try {
        while (sha1Stream.read != -1) {}
        Option(bytesToString(sha1Stream.getMessageDigest.digest))
      } catch {
        case NonFatal(e) => None
      } finally {
        sha1Stream.close()
      }
    }
  }

  def download(url: String, file: File): Try[File] =
    Try {
      import sys.process._
      val parentDir = file.getAbsoluteFile().getParentFile() match {
        case null =>
          new File(".")
        case parent =>
          if (!parent.exists()) {
            parent.mkdirs()
          }
          parent
      }

      val tmpFile = File.createTempFile(s".${file.getName()}", "", parentDir)
      try {
        val fileStream = new FileOutputStream(tmpFile)
        val outStream = new BufferedOutputStream(fileStream)
        try {

          val connection = new URL(url).openConnection
          connection.setRequestProperty("User-Agent", "Blended Updater")
          val inStream = new BufferedInputStream(connection.getInputStream())
          try {
            val bufferSize = 1024
            var buffer = new Array[Byte](bufferSize)

            while (inStream.read(buffer, 0, bufferSize) match {
              case count if count < 0 => false
              case count => {
                outStream.write(buffer, 0, count)
                true
              }
            }) {}
          } finally {
            inStream.close()
          }
        } finally {
          outStream.flush()
          outStream.close()
          fileStream.flush()
          fileStream.close()
        }

        Files.move(Paths.get(tmpFile.toURI()), Paths.get(file.toURI()),
          StandardCopyOption.ATOMIC_MOVE);

        file
      } catch {
        case NonFatal(e) =>
          if (tmpFile.exists()) {
            tmpFile.delete()
          }
          throw e
      }

    }

  def bundlesBaseDir(baseDir: File): File = new File(baseDir, "bundles")

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

  def bundleLocation(bundle: BundleConfig, baseDir: File): File =
    new File(RuntimeConfig.bundlesBaseDir(baseDir), bundle.jarName.getOrElse(resolveFileName(bundle.url).get))

  def bundleLocation(artifact: Artifact, baseDir: File): File =
    new File(RuntimeConfig.bundlesBaseDir(baseDir), artifact.fileName.getOrElse(resolveFileName(artifact.url).get))

  def resourceArchiveLocation(resourceArchive: Artifact, baseDir: File): File =
    new File(baseDir, s"resources/${resourceArchive.fileName.getOrElse(resolveFileName(resourceArchive.url).get)}")

  def resourceArchiveTouchFileLocation(resourceArchive: Artifact, baseDir: File, mvnBaseUrl: Option[String]): File = {
    val resFile = resourceArchiveLocation(resourceArchive, baseDir)
    new File(resFile.getParentFile(), s".${resFile.getName()}")
  }

  def getPropertyFileProvider(
    curRuntime: RuntimeConfig,
    prevRuntime: Option[LocalRuntimeConfig]): Try[immutable.Seq[PropertyProvider]] = Try {
    curRuntime.properties.get(Properties.PROFILE_PROPERTY_PROVIDERS).toList.flatMap(_.split("[,]")).flatMap {
      case "env" => Some(new EnvPropertyProvider())
      case "sysprop" => Some(new SystemPropertyProvider())
      case x if x.startsWith("uuid:") =>
        val arg = x.substring("uuid:".size)
        if (arg.size == 0) sys.error(s"Invalid property key argument given with ${x}. Syntax: uuid:<key>")
        Some(new UuidPropertyProvider(arg))
      case x if x.startsWith("fileCurVer:") =>
        val arg = x.substring("fileCurVer:".size).trim()
        if (arg.size == 0) sys.error(s"Invalid filename argument given with ${x}. Syntax: fileCurVer:<filename>")
        // Only create file based property provider if we have the same profile name
        prevRuntime.find(_.runtimeConfig.name == curRuntime.name).map { prev =>
          val file = new File(prev.baseDir, arg)
          new FilePropertyProvider(file)
        }
      case pType => sys.error(s"Unsupported provider type: ${pType}")
    }
  }

  def createPropertyFile(curRuntime: LocalRuntimeConfig,
    prevRuntime: Option[LocalRuntimeConfig]): Option[Try[File]] = createPropertyFile(curRuntime, prevRuntime, false)

  def createPropertyFile(curRuntime: LocalRuntimeConfig,
    prevRuntime: Option[LocalRuntimeConfig], onlyIfMisssing: Boolean): Option[Try[File]] = {

    curRuntime.runtimeConfig.properties.get(Properties.PROFILE_PROPERTY_FILE).flatMap { fileName =>
      val propFile = new File(curRuntime.baseDir, fileName)
      if (propFile.exists() && onlyIfMisssing) {
        // nothing to create, as the file already exists
        None
      } else {
        propFile.getParentFile.mkdirs()
        val content = new Properties()
        if (propFile.exists()) {
          content.load(new BufferedReader(new FileReader(propFile)))
        }

        curRuntime.runtimeConfig.properties.get(Properties.PROFILE_PROPERTY_KEYS).map(_.split("[,]").toList).map { props =>
          val providers = getPropertyFileProvider(curRuntime.runtimeConfig, prevRuntime).get
          if (providers.isEmpty) sys.error(s"No property providers defined (${Properties.PROFILE_PROPERTY_PROVIDERS})")
          val resolvedProps = props.map { prop =>
            val newValue = providers.toStream.map(_.provide(prop)).find(_.isDefined).map(_.get)

            newValue match {
              case None => (prop, Option(content.getProperty(prop)).getOrElse(sys.error(s"Could not find property value for key [${prop}]")))
              case Some(v) => (prop, v)
            }
          }

          resolvedProps.foreach { case (k, v) => content.setProperty(k, v) }
          val writer = new FileWriter(propFile)
          try {
            content.store(writer, "Generated by blended updater")
          } finally {
            writer.close()
          }
          Success(propFile)
        }
      }
    }

  }

}
