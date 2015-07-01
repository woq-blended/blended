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

case class RuntimeConfig(
    name: String,
    version: String,
    bundles: Seq[BundleConfig] = Seq(),
    startLevel: Int,
    defaultStartLevel: Int,
    properties: Map[String, String] = Map(),
    frameworkProperties: Map[String, String] = Map(),
    systemProperties: Map[String, String] = Map(),
    fragments: Seq[FragmentConfig] = Seq(),
    resources: Seq[Artifact] = Seq()) {

  def mvnBaseUrl: Option[String] = properties.get(RuntimeConfig.Properties.MVN_REPO)

  def resolveBundleUrl(url: String): Try[String] = Try {
    if (url.startsWith("mvn:")) {
      mvnBaseUrl match {
        case Some(base) => MvnGav.parse(url.substring(4)).get.toUrl(base)
        case None => sys.error("No repository defined to resolve url: " + url)
      }
    } else url
  }

  def resolveFileName(url: String): Try[String] =
    resolveBundleUrl(url).flatMap { url =>
      Try {
        val path = new URL(url).getPath()
        path.split("[/]").filter(!_.isEmpty()).reverse.headOption.getOrElse(path)
      }
    }

  def allBundles: Seq[BundleConfig] = bundles ++ fragments.flatMap(_.bundles)

  val framework: BundleConfig = {
    val fs = allBundles.filter(b => b.startLevel == Some(0))
    require(fs.size == 1, "A RuntimeConfig needs exactly one bundle with startLevel '0', but this one has: " + fs.size)
    fs.head
  }

  def baseDir(profileBaseDir: File): File = new File(profileBaseDir, s"${name}/${version}")

  def bundleLocation(bundle: BundleConfig, baseDir: File): File =
    new File(RuntimeConfig.bundlesBaseDir(baseDir), bundle.jarName.getOrElse(resolveFileName(bundle.url).get))

  def bundleLocation(artifact: Artifact, baseDir: File): File =
    new File(RuntimeConfig.bundlesBaseDir(baseDir), artifact.fileName.getOrElse(resolveFileName(artifact.url).get))

  def profileFileLocation(baseDir: File): File =
    new File(baseDir, "profile.conf")

  def resourceArchiveLocation(resourceArchive: Artifact, baseDir: File): File =
    new File(baseDir, s"resources/${resourceArchive.fileName.getOrElse(resolveFileName(resourceArchive.url).get)}")

  def resourceArchiveTouchFileLocation(resourceArchive: Artifact, baseDir: File): File = {
    val resFile = resourceArchiveLocation(resourceArchive, baseDir)
    new File(resFile.getParentFile(), s".${resFile.getName()}")
  }

  def createResourceArchiveTouchFile(resourceArchive: Artifact, resourceArchiveChecksum: Option[String], baseDir: File): Try[File] = Try {
    val file = resourceArchiveTouchFileLocation(resourceArchive, baseDir)
    Option(file.getParentFile()).foreach { parent =>
      parent.mkdirs()
    }
    val os = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))
    try {
      os.println(resourceArchiveChecksum.getOrElse(""))
    } finally {
      os.close()
    }
    file
  }
}

object RuntimeConfig {

  object Properties {
    val PROFILES_DIR = "blended.updater.profiles.dir"
    val PROFILE_NAME = "blended.updater.profile.name"
    val PROFILE_VERSION = "blended.updater.profile.version"
    val PROFILE_LOOKUP_FILE = "blended.updater.profile.lookup.file"
    val MVN_REPO = "blended.updater.mvn.url"
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
      "fragments" -> runtimeConfig.fragments.map(FragmentConfig.toConfig).map(_.root().unwrapped()).asJava,
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

  def validate(baseDir: File, config: RuntimeConfig,
    includeResourceArchives: Boolean,
    explodedResourceArchives: Boolean): Seq[String] = {
    val artifacts = config.allBundles.map(b => config.bundleLocation(b, baseDir) -> b.artifact) ++
      (if (includeResourceArchives) config.resources.map(r => config.resourceArchiveLocation(r, baseDir) -> r) else Seq())

    val artifactIssues = artifacts.flatMap {
      case (file, artifact) =>
        val issue = if (!file.exists()) {
          Some(s"Missing file: ${file.getName()}")
        } else {
          RuntimeConfig.digestFile(file) match {
            case Some(d) =>
              if (Option(d) != artifact.sha1Sum) {
                Some(s"Invalid checksum of bundle jar: ${file.getName()}")
              } else None
            case None =>
              Some(s"Could not evaluate checksum of bundle jar: ${file.getName()}")
          }
        }
        issue.toList
    }

    val resourceIssues = if (explodedResourceArchives) {
      config.resources.flatMap { artifact =>
        val touchFile = config.resourceArchiveTouchFileLocation(artifact, baseDir)
        if (touchFile.exists()) {
          val persistedChecksum = Source.fromFile(touchFile).getLines().mkString("\n")
          if (persistedChecksum != artifact.sha1Sum) {
            List(s"Resource ${artifact.fileName} was unpacked from an archive with a different checksum.")
          } else Nil
        } else {
          List(s"Resource ${artifact.fileName.getOrElse(config.resolveFileName(artifact.url).get)} not unpacked")
        }
      }
    } else Nil

    artifactIssues ++ resourceIssues
  }

  def bundlesBaseDir(baseDir: File): File = new File(baseDir, "bundles")

}
