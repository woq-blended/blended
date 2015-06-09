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

object RuntimeConfig {

  case class BundleConfig(
    url: String,
    jarName: String,
    sha1Sum: String,
    start: Boolean,
    startLevel: Option[Int])

  case class FragmentConfig(
    name: String,
    version: String,
    bundles: Seq[BundleConfig])

  def read(config: Config): RuntimeConfig = {
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

    def readBundle(bc: Config) = BundleConfig(

      url = bc.getString("url"),
      jarName = bc.getString("jarName"),
      sha1Sum = bc.getString("sha1Sum"),
      start = if (bc.hasPath("start")) bc.getBoolean("start") else false,
      startLevel = if (bc.hasPath("startLevel")) Option(bc.getInt("startLevel")) else None
    )

    RuntimeConfig(
      name = config.getString("name"),
      version = config.getString("version"),
      // framework = readBundle(config.getConfig("framework")),
      bundles =
        if (config.hasPath("bundles"))
          config.getObjectList("bundles").asScala.map { bc => readBundle(bc.toConfig()) }.toList
        else Seq(),
      startLevel = config.getInt("startLevel"),
      defaultStartLevel = config.getInt("defaultStartLevel"),
      frameworkProperties = configAsMap("frameworkProperties", Some(() => Map())),
      systemProperties = configAsMap("systemProperties", Some(() => Map())),
      fragments =
        if (config.hasPath("fragments"))
          config.getObjectList("fragments").asScala.map { f =>
          val fc = f.toConfig()
          FragmentConfig(
            name = fc.getString("name"),
            version = fc.getString("version"),
            bundles = config.getObjectList("bundles").asScala.map { bc => readBundle(bc.toConfig()) }.toList
          )
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

case class RuntimeConfig(
    name: String,
    version: String,
    //    framework: BundleConfig,
    bundles: Seq[RuntimeConfig.BundleConfig],
    startLevel: Int,
    defaultStartLevel: Int,
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
