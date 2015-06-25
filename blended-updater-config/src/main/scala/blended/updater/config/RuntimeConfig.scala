package blended.updater.config

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Formatter
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.immutable.Map
import scala.collection.immutable.Seq
import scala.util.control.NonFatal
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import java.io.FileOutputStream
import java.net.URL
import java.io.BufferedOutputStream
import scala.util.Try
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object RuntimeConfig {

  object Properties {
    val PROFILES_DIR = "blended.updater.profiles.dir"
    val PROFILE_NAME = "blended.updater.profile.name"
    val PROFILE_VERSION = "blended.updater.profile.version"
    val MVN_REPO = "blended.updater.mvn.url"
  }
  
  case class BundleConfig(
    url: String,
    jarName: String,
    sha1Sum: String,
    start: Boolean,
    startLevel: Option[Int])

  object BundleConfig {
    def read(config: Config): Try[BundleConfig] = Try {
      BundleConfig(
        url = config.getString("url"),
        jarName = config.getString("jarName"),
        sha1Sum = config.getString("sha1Sum"),
        start = if (config.hasPath("start")) config.getBoolean("start") else false,
        startLevel = if (config.hasPath("startLevel")) Option(config.getInt("startLevel")) else None
      )
    }
  }

  case class FragmentConfig(
    name: String,
    version: String,
    bundles: Seq[BundleConfig])

  object FragmentConfig {
    def read(config: Config): Try[FragmentConfig] = Try {
      FragmentConfig(
        name = config.getString("name"),
        version = config.getString("version"),
        bundles =
          if (config.hasPath("bundles")) {
            config.getObjectList("bundles").asScala.map { bc => BundleConfig.read(bc.toConfig()).get }.toList
          } else Nil
      )
    }
  }

  def read(config: Config, fragmentRepo: Seq[FragmentConfig] = Seq()): Try[RuntimeConfig] = Try {

    // TODO: ensure, all fragments are non-empty

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

    val bundleReference = ConfigFactory.parseResources(getClass(), "RuntimeConfig.BundleConfig-reference.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    val bundleOptionals = ConfigFactory.parseResources(getClass(), "RuntimeConfig.BundleConfig-optional.conf", ConfigParseOptions.defaults().setAllowMissing(false))

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
      properties = configAsMap("properties", Some(() => Map())),
      frameworkProperties = configAsMap("frameworkProperties", Some(() => Map())),
      systemProperties = configAsMap("systemProperties", Some(() => Map())),
      fragments =
        if (config.hasPath("fragments"))
          config.getObjectList("fragments").asScala.map { f =>
          val fc = f.toConfig()
          if (fc.hasPath("bundles")) {
            // read directly
            FragmentConfig.read(fc).get
          } else {
            // lookup in repo
            val fName = fc.getString("name")
            val fVersion = fc.getString("version")
            fragmentRepo.find(f => f.name == fName && f.version == fVersion) match {
              case Some(f) => f
              case None => sys.error(s"Could not found bundles for fragment: ${fName}-${fVersion}")
            }
          }
        }.toList
        else Seq()
    )
  }

  def toConfig(runtimeConfig: RuntimeConfig): Config = {
    def bundle(bundle: BundleConfig) = (
      Map(
        "url" -> bundle.url,
        "jarName" -> bundle.jarName,
        "sha1Sum" -> bundle.sha1Sum,
        "start" -> bundle.start
      ) ++ bundle.startLevel.map(sl => Map("startLevel" -> sl)).getOrElse(Map())
    ).asJava

    val config = Map(
      "name" -> runtimeConfig.name,
      "version" -> runtimeConfig.version,
      "bundles" -> runtimeConfig.bundles.map { b => bundle(b) }.asJava,
      "startLevel" -> runtimeConfig.startLevel,
      "defaultStartLevel" -> runtimeConfig.defaultStartLevel,
      "frameworkProperties" -> runtimeConfig.frameworkProperties.asJava,
      "systemProperties" -> runtimeConfig.systemProperties.asJava,
      "fragments" -> runtimeConfig.fragments.map { f =>
        Map(
          "name" -> f.name,
          "version" -> f.version,
          "bundles" -> f.bundles.map { b => bundle(b) }.asJava
        ).asJava
      }.asJava
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

  def validate(baseDir: File, config: RuntimeConfig): Seq[String] = {
    config.allBundles.flatMap { b =>
      val jar = new File(baseDir, b.jarName)
      val issue = if (!jar.exists()) {
        Some(s"Missing bundle jar: ${b.jarName}")
      } else {
        RuntimeConfig.digestFile(jar) match {
          case Some(d) =>
            if (d != b.sha1Sum) {
              Some(s"Invalid checksum of bundle jar: ${b.jarName}")
            } else None
          case None =>
            Some(s"Could not evaluate checksum of bundle jar: ${b.jarName}")
        }
      }
      issue.toList
    }
  }

}

case class RuntimeConfig(
    name: String,
    version: String,
    bundles: Seq[RuntimeConfig.BundleConfig],
    startLevel: Int,
    defaultStartLevel: Int,
    properties: Map[String, String],
    frameworkProperties: Map[String, String],
    systemProperties: Map[String, String],
    fragments: Seq[RuntimeConfig.FragmentConfig]) {

  def allBundles: Seq[RuntimeConfig.BundleConfig] = bundles ++ fragments.flatMap(_.bundles)

  val framework: RuntimeConfig.BundleConfig = {
    val fs = allBundles.filter(b => b.startLevel == Some(0))
    require(fs.size == 1, "A RuntimeConfig needs exactly one bundle with startLevel '0', but this one has: " + fs.size)
    fs.head
  }

}
