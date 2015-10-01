package blended.updater.config

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Formatter
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.immutable.Map
import scala.collection.immutable.Seq
import scala.util.Try
import scala.util.control.NonFatal
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import java.io.PrintStream
import scala.io.Source
import java.util.Properties
import java.io.FileWriter
import scala.util.Success
import java.io.FileReader
import java.io.BufferedReader

object RuntimeConfig {

  val MvnPrefix = "mvn:"

  object Properties {
    val PROFILES_BASE_DIR = "blended.updater.profiles.basedir"
    val PROFILE_DIR = "blended.updater.profile.dir"
    val PROFILE_NAME = "blended.updater.profile.name"
    val PROFILE_VERSION = "blended.updater.profile.version"
    val PROFILE_LOOKUP_FILE = "blended.updater.profile.lookup.file"
    val MVN_REPO = "blended.updater.mvn.url"
    /** A properties file relative to the profile dir */
    val PROFILE_PROPERTY_FILE = "blended.updater.profile.properties.file"
    /** Comma separated list of property providers */
    val PROFILE_PROPERTY_PROVIDERS = "blended.updater.profile.properties.providers"
    /** Comma separated list of properties required to be in the properties file */
    val PROFILE_PROPERTY_KEYS = "blended.updater.profile.properties.keys"
  }

  def read(config: Config, featureRepo: Seq[FeatureConfig] = Seq()): Try[RuntimeConfig] = Try {

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
      // framework = readBundle(config.getConfig("framework")),
      bundles =
        if (config.hasPath("bundles"))
          config.getObjectList("bundles").asScala.map { bc => BundleConfig.read(bc.toConfig()).get }.toList
        else Seq(),
      startLevel = config.getInt("startLevel"),
      defaultStartLevel = config.getInt("defaultStartLevel"),
      properties = properties,
      frameworkProperties = configAsMap("frameworkProperties", Some(() => Map())),
      systemProperties = configAsMap("systemProperties", Some(() => Map())),
      features =
        if (config.hasPath("features"))
          config.getObjectList("features").asScala.map { f =>
          val fc = f.toConfig()
          if (fc.hasPath("bundles")) {
            // read directly
            FeatureConfig.read(fc).get
          } else {
            // lookup in repo
            val fName = fc.getString("name")
            val fVersion = fc.getString("version")
            featureRepo.find(f => f.name == fName && f.version == fVersion) match {
              case Some(f) => f
              case None => sys.error(s"Could not find bundles for feature: ${fName}-${fVersion}")
            }
          }
        }.toList
        else Seq(),
      resources =
        if (config.hasPath("resources"))
          config.getObjectList("resources").asScala.map(r => Artifact.read(r.toConfig()).get).toList
        else Seq()
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
      "features" -> runtimeConfig.features.map(FeatureConfig.toConfig).map(_.root().unwrapped()).asJava,
      "resources" -> runtimeConfig.resources.map(Artifact.toConfig).map(_.root().unwrapped()).asJava
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
        Some(bytesToString(sha1Stream.getMessageDigest.digest))
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

        val outStream = new BufferedOutputStream(new FileOutputStream(tmpFile))
        try {

          val connection = new URL(url).openConnection
          connection.setRequestProperty("User-Agent", "Blended Updater")
          val inStream = new BufferedInputStream(connection.getInputStream())
          try {
            val bufferSize = 1024
            var break = false
            var len = 0
            var buffer = new Array[Byte](bufferSize)

            while (!break) {
              inStream.read(buffer, 0, bufferSize) match {
                case x if x < 0 => break = true
                case count => {
                  len = len + count
                  outStream.write(buffer, 0, count)
                }
              }
            }
          } finally {
            inStream.close()
          }
        } finally {
          outStream.flush()
          outStream.close()
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
    prevRuntime: Option[LocalRuntimeConfig]): Try[Seq[PropertyProvider]] = Try {
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
    prevRuntime: Option[LocalRuntimeConfig]): Option[Try[File]] = {

    curRuntime.runtimeConfig.properties.get(Properties.PROFILE_PROPERTY_FILE).flatMap { fileName =>
      val propFile = new File(curRuntime.baseDir, fileName)
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

case class RuntimeConfig(
    name: String,
    version: String,
    bundles: Seq[BundleConfig] = Seq(),
    startLevel: Int,
    defaultStartLevel: Int,
    properties: Map[String, String] = Map(),
    frameworkProperties: Map[String, String] = Map(),
    systemProperties: Map[String, String] = Map(),
    features: Seq[FeatureConfig] = Seq(),
    resources: Seq[Artifact] = Seq()) {

  import RuntimeConfig._

  def mvnBaseUrl: Option[String] = properties.get(RuntimeConfig.Properties.MVN_REPO)

  def resolveBundleUrl(url: String): Try[String] = RuntimeConfig.resolveBundleUrl(url, mvnBaseUrl)

  def resolveFileName(url: String): Try[String] = RuntimeConfig.resolveFileName(url)

  def allBundles: Seq[BundleConfig] = bundles ++ features.flatMap(_.bundles)

  val framework: BundleConfig = {
    val fs = allBundles.filter(b => b.startLevel == Some(0))
    require(fs.size == 1, "A RuntimeConfig needs exactly one bundle with startLevel '0', but this one has: " + fs.size)
    fs.head
  }

  def baseDir(profileBaseDir: File): File = new File(profileBaseDir, s"${name}/${version}")

  def localRuntimeConfig(baseDir: File): LocalRuntimeConfig = LocalRuntimeConfig(runtimeConfig = this, baseDir = baseDir)

}
