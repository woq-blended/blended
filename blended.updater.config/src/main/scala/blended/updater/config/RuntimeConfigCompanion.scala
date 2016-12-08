package blended.updater.config

import java.io._
import java.net.URL
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.security.{DigestInputStream, MessageDigest}
import java.util.{Formatter, Properties}

import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}

import scala.collection.JavaConverters.{asScalaBufferConverter, asScalaSetConverter, mapAsJavaMapConverter, seqAsJavaListConverter}
import scala.util.{Success, Try}
import scala.util.control.NonFatal

object RuntimeConfigCompanion {

  def read(config: Config): Try[RuntimeConfig] = Try {

    val optionals = ConfigFactory.parseResources(getClass(), "RuntimeConfig-optional.conf", ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
    val reference = ConfigFactory.parseResources(getClass(), "RuntimeConfig-reference.conf", ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
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
          config.getObjectList("bundles").asScala.map { bc => BundleConfigCompanion.read(bc.toConfig()).get }.toList
        else List.empty,
      startLevel = config.getInt("startLevel"),
      defaultStartLevel = config.getInt("defaultStartLevel"),
      properties = properties,
      frameworkProperties = configAsMap("frameworkProperties", Some(() => Map())),
      systemProperties = configAsMap("systemProperties", Some(() => Map())),
      features =
        if (config.hasPath("features"))
          config.getObjectList("features").asScala.map { f =>
          FeatureRefCompanion.fromConfig(f.toConfig()).get
        }.toList
        else List.empty,
      resources =
        if (config.hasPath("resources"))
          config.getObjectList("resources").asScala.map(r => ArtifactCompanion.read(r.toConfig()).get).toList
        else List.empty,
      resolvedFeatures =
        if (config.hasPath("resolvedFeatures"))
          config.getObjectList("resolvedFeatures").asScala.map(r => FeatureConfigCompanion.read(r.toConfig()).get).toList
        else List.empty
    )
  }

  def toConfig(runtimeConfig: RuntimeConfig): Config = {
    val config = Map(
      "name" -> runtimeConfig.name,
      "version" -> runtimeConfig.version,
      "bundles" -> runtimeConfig.bundles.map(BundleConfigCompanion.toConfig).map(_.root().unwrapped()).asJava,
      "startLevel" -> runtimeConfig.startLevel,
      "defaultStartLevel" -> runtimeConfig.defaultStartLevel,
      "properties" -> runtimeConfig.properties.asJava,
      "frameworkProperties" -> runtimeConfig.frameworkProperties.asJava,
      "systemProperties" -> runtimeConfig.systemProperties.asJava,
      "features" -> runtimeConfig.features.map(FeatureRefCompanion.toConfig).map(_.root().unwrapped()).asJava,
      "resources" -> runtimeConfig.resources.map(ArtifactCompanion.toConfig).map(_.root().unwrapped()).asJava,
      "resolvedFeatures" -> runtimeConfig.resolvedFeatures.map(FeatureConfigCompanion.toConfig).map(_.root().unwrapped()).asJava
    ).asJava

    ConfigFactory.parseMap(config).resolve()
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

  def bundleLocation(bundle: BundleConfig, baseDir: File): File =
    new File(RuntimeConfigCompanion.bundlesBaseDir(baseDir), bundle.jarName.getOrElse(RuntimeConfig.resolveFileName(bundle.url).get))

  def bundleLocation(artifact: Artifact, baseDir: File): File =
    new File(RuntimeConfigCompanion.bundlesBaseDir(baseDir), artifact.fileName.getOrElse(RuntimeConfig.resolveFileName(artifact.url).get))

  def resourceArchiveLocation(resourceArchive: Artifact, baseDir: File): File =
    new File(baseDir, s"resources/${resourceArchive.fileName.getOrElse(RuntimeConfig.resolveFileName(resourceArchive.url).get)}")

  def resourceArchiveTouchFileLocation(resourceArchive: Artifact, baseDir: File, mvnBaseUrl: Option[String]): File = {
    val resFile = resourceArchiveLocation(resourceArchive, baseDir)
    new File(resFile.getParentFile(), s".${resFile.getName()}")
  }

  def getPropertyFileProvider(
    curRuntime: RuntimeConfig,
    prevRuntime: Option[LocalRuntimeConfig]): Try[List[PropertyProvider]] = Try {
    curRuntime.properties.get(RuntimeConfig.Properties.PROFILE_PROPERTY_PROVIDERS).toList.flatMap(_.split("[,]")).flatMap {
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

    curRuntime.runtimeConfig.properties.get(RuntimeConfig.Properties.PROFILE_PROPERTY_FILE).flatMap { fileName =>
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

        curRuntime.runtimeConfig.properties.get(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS).map(_.split("[,]").toList).map { props =>
          val providers = getPropertyFileProvider(curRuntime.runtimeConfig, prevRuntime).get
          if (providers.isEmpty) sys.error(s"No property providers defined (${RuntimeConfig.Properties.PROFILE_PROPERTY_PROVIDERS})")
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
