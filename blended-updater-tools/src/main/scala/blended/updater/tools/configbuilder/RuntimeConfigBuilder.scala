package blended.updater.tools.configbuilder

import de.tototec.cmdoption.CmdOption
import java.net.URL
import blended.updater.config.RuntimeConfig
import java.net.URI
import java.io.File
import de.tototec.cmdoption.CmdCommand
import scala.collection.immutable._
import de.tototec.cmdoption.CmdlineParser
import scala.util.Try
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigFactory
import blended.updater.config.ConfigWriter

object RuntimeConfigBuilder {

  class CmdOptions {
    @CmdOption(names = Array("-h", "--help"), isHelp = true)
    var help: Boolean = false

    @CmdOption(names = Array("-d", "--download-missing"))
    var downloadMissing: Boolean = false

    @CmdOption(names = Array("-u", "--update-checksums"))
    var updateChecksums: Boolean = false

    @CmdOption(names = Array("-c", "--check"))
    var check: Boolean = false

    @CmdOption(names = Array("-f"), args = Array("configfile"))
    var configFile: String = ""
  }

  case class Options(
    start: Boolean = false,
    startLevel: Option[Int] = None,
    sha1Sum: Option[String] = None,
    fragment: Option[(String, String)] = None,
    url: Option[String] = None)

  class CmdlineApi {

    private[this] var bundleOptions: Options = Options()

    private[this] var bundles: Seq[Options] = Seq()

    private[this] var profile: Option[(String, String)] = None

    private[this] var output: Option[String] = None

    private[this] var startLevel: Option[Int] = None

    @CmdOption(names = Array("-L", "--start-level"), args = Array("level"), description = "Set the start level of the framework")
    def frageworkStartLevel(startLevel: Int): Unit = this.startLevel = Option(startLevel)

    private[this] var baseUrl: Option[String] = None
    @CmdOption(names = Array("-B", "--base-url"), args = Array("URL"),
      description = "Set a base URL used to locate bundles")
    def setBaseUrl(url: String): Unit = {

    }

    @CmdOption(args = Array("URL"),
      description = "Add a bundle from the given {0} (partial URLs are supported if a base URL is defined")
    def addBundle(url: String): Unit = {
      val bundleUrl = s"${baseUrl.map(_ + "/").getOrElse("")}${url}"
      bundles :+= bundleOptions.copy(url = Option(bundleUrl))
      bundleOptions = Options()
    }

    @CmdOption(names = Array("-s", "--start"), description = "Enable the start flag for the next bundle")
    def nextBundleStart(): Unit = {
      bundleOptions = bundleOptions.copy(start = true)
    }

    @CmdOption(names = Array("-l", "--bundle-start-level"), args = Array("level"), description = "Set the start level for the next bundle")
    def nextBundleStartLevel(startLevel: Int): Unit = {
      bundleOptions = bundleOptions.copy(startLevel = Option(startLevel))
    }

    @CmdOption(names = Array("-p", "--profile"), args = Array("name", "version"), description = "Set the name of this profile")
    def setProfile(name: String, version: String): Unit = {
      profile = Option(name -> version)
    }

    @CmdOption(names = Array("-o", "--output"), args = Array("file"), description = "To config file to be buit")
    def setOutput(name: String): Unit = {
      output = Option(name)
    }

    def build(): Try[RuntimeConfig] = Try {
      val (name, version) = profile match {
        case Some(x) => x
        case None => throw new RuntimeException("Missing profile name and version configuration")
      }
      startLevel match {
        case Some(x) => x
        case None => throw new RuntimeException("Missing start level configuration")
      }

      val bundleConfigs = bundles.map { b =>

        val bundleUrl = new URL(b.url.get)
        val sha1Sum = b.sha1Sum.getOrElse {
          // TODO: eval sha1Sum
          ???
        }

        val bc = RuntimeConfig.BundleConfig(

          url = bundleUrl.toExternalForm(),
          jarName = new File(bundleUrl.getPath()).getName(),
          sha1Sum = sha1Sum,
          start = b.start,
          startLevel = b.startLevel
        )

        b.fragment -> bc
      }

      val groupedBundles = bundleConfigs.toSeq.groupBy(b => b._1).mapValues(b => b.unzip._2)

      val nonFragmentBundles = groupedBundles.getOrElse(None, Seq())

      val fragments = groupedBundles.flatMap {
        case (Some((n, v)), bs) =>
          RuntimeConfig.FragmentConfig(
            name = n,
            version = v,
            bundles = bs
          ) :: Nil
        case _ => Nil
      }.toList

      RuntimeConfig(
        name = name,
        version = version,
        bundles = nonFragmentBundles,
        fragments = fragments,
        frameworkProperties = Map(),
        systemProperties = Map(),
        startLevel = ???,
        defaultStartLevel = ???
      )
    }

    @CmdCommand(names = Array("build"), description = "Build a new config file")
    class Build(initialConfig: Config) {

      private[this] var config: Config = initialConfig

      @CmdOption(names = Array("-L", "--start-level"), args = Array("level"), description = "Set the start level of the framework")
      def frageworkStartLevel(startLevel: Int): Unit =
        config = config.withValue("startLevel", ConfigValueFactory.fromAnyRef(startLevel))

      private[this] var baseUrl: Option[String] = None

      @CmdOption(names = Array("-B", "--base-url"), args = Array("URL"),
        description = "Set a base URL used to locate bundles")
      def setBaseUrl(url: String): Unit = baseUrl = url.trim() match {
        case "" => None
        case x => Option(x)
      }

      private[this] var bundleOptions: Options = Options()

      @CmdOption(names = Array("-s", "--start"), description = "Enable the start flag for the next bundle")
      def nextBundleStart(): Unit = {
        bundleOptions = bundleOptions.copy(start = true)
      }

      @CmdOption(names = Array("-l", "--bundle-start-level"), args = Array("level"), description = "Set the start level for the next bundle")
      def nextBundleStartLevel(startLevel: Int): Unit = {
        bundleOptions = bundleOptions.copy(startLevel = Option(startLevel))
      }

      @CmdOption(names = Array("-p", "--profile"), args = Array("name", "version"), description = "Set the name of this profile")
      def setProfile(name: String, version: String): Unit = {
        profile = Option(name -> version)
      }

      @CmdOption(args = Array("URL"),
        description = "Add a bundle from the given {0} (partial URLs are supported if a base URL is defined")
      def addBundle(url: String): Unit = {
        val bundleUrl = s"${baseUrl.map(_ + "/").getOrElse("")}${url}"
        // bundleOptions.copy(url = Option(bundleUrl)).toConfig()

        bundleOptions.startLevel.map(l => "startLevel" -> l)
        bundleOptions

        bundleOptions = Options()
      }

    }

  }

  def main(args: Array[String]): Unit = {
    println(s"RuntimeConfigBuilder: ${args.mkString(" ")}")

    val options = new CmdOptions()
    //    val api = new CmdlineApi()

    val cp = new CmdlineParser(options)
    cp.parse(args: _*)
    if (options.help) {
      cp.usage()
      sys.exit(0)
    }

    if (options.configFile == "") {
      sys.error("No config file given")
    }

    val configFile = new File(options.configFile).getAbsoluteFile()
    val dir = configFile.getParentFile()
    val config = ConfigFactory.parseFile(new File(options.configFile)).resolve()
    var runtimeConfig = RuntimeConfig.read(config)

    if (options.check) {
      val issues = runtimeConfig.bundles.flatMap { b =>
        val jar = new File(dir, b.jarName)
        val issue = if (!jar.exists()) {
          Some(s"Missing bundle jar: ${jar}")
        } else {
          RuntimeConfig.digestFile(jar) match {
            case Some(d) =>
              if (d != b.sha1Sum) {
                Some(s"Invalid checksum of bundle jar: ${jar}")
              } else None
            case None =>
              Some(s"Could not evaluate checksum of bundle jar: ${jar}")
          }
        }
        issue.foreach { println }
        issue.toList
      }
      if (!issues.isEmpty) {
        sys.exit(1)
      }
    }

    if (options.downloadMissing) {
      runtimeConfig.bundles.foreach { b =>
        val jar = new File(dir, b.jarName)
        if (!jar.exists()) {
          println(s"Downloading: ${jar}")
          RuntimeConfig.download(b.url, jar)
        }
      }
    }

    if (options.updateChecksums) {
      val newRuntimeConfig = runtimeConfig.bundles.foldLeft(runtimeConfig) { (c, b) =>
        val jar = new File(dir, b.jarName)
        RuntimeConfig.digestFile(jar).map { checksum =>
          if (b.sha1Sum != checksum) {
            println(s"Updating checksum for bundle: ${b.jarName}")
            c.copy(
              bundles = b.copy(sha1Sum = checksum) +: (c.bundles.filter { _ != b })
            )
          } else {
            c
          }
        }.getOrElse(c)
      }
      if (runtimeConfig != newRuntimeConfig) {
        println("Updating config file: " + configFile)
        ConfigWriter.write(RuntimeConfig.toConfig(newRuntimeConfig), configFile, None)
      }
    }

  }

}

