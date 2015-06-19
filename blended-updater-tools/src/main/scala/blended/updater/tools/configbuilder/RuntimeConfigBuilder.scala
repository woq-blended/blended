package blended.updater.tools.configbuilder

import java.io.File

import com.typesafe.config.ConfigFactory

import blended.updater.config.ConfigWriter
import blended.updater.config.RuntimeConfig
import de.tototec.cmdoption.CmdOption
import de.tototec.cmdoption.CmdlineParser
import scala.collection.JavaConverters._
import scala.collection.immutable._

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

    @CmdOption(names = Array("-r", "--fragment-repo"), args = Array("file"),
      description = "Lookup fragments from {0}",
      maxCount = -1
    )
    def addFragmentRepo(repo: String): Unit = fragmentRepos +:= repo
    var fragmentRepos: Seq[String] = Seq()
  }

  def main(args: Array[String]): Unit = {
    println(s"RuntimeConfigBuilder: ${args.mkString(" ")}")

    val options = new CmdOptions()

    val cp = new CmdlineParser(options)
    cp.parse(args: _*)
    if (options.help) {
      cp.usage()
      sys.exit(0)
    }

    if (options.configFile == "") {
      sys.error("No config file given")
    }

    // read fragment repo files
    val fragments = options.fragmentRepos.flatMap { fileName => 
      val repoConfig = ConfigFactory.parseFile(new File(fileName)).resolve()
      repoConfig.getObjectList("fragments").asScala.map { c =>
    	  RuntimeConfig.FragmentConfig.read(c.toConfig()).get
      }
    }
    
    val configFile = new File(options.configFile).getAbsoluteFile()
    val dir = configFile.getParentFile()
    val config = ConfigFactory.parseFile(configFile).resolve()
    var runtimeConfig = RuntimeConfig.read(config, fragments).get

    if (options.check) {
      val issues = RuntimeConfig.validate(dir, runtimeConfig)
      if (!issues.isEmpty) {
        println(issues.mkString("\n"))
        sys.exit(1)
      }
    }

    if (options.downloadMissing) {
      runtimeConfig.allBundles.foreach { b =>
        val jar = new File(dir, b.jarName)
        if (!jar.exists()) {
          println(s"Downloading: ${jar}")
          RuntimeConfig.download(b.url, jar)
        }
      }
    }

    if (options.updateChecksums) {
      def checkAndupdateBundle(b: RuntimeConfig.BundleConfig): RuntimeConfig.BundleConfig = {
        val jar = new File(dir, b.jarName)
        RuntimeConfig.digestFile(jar).map { checksum =>
          if (b.sha1Sum != checksum) {
            println(s"Updating checksum for bundle: ${b.jarName}")
            b.copy(sha1Sum = checksum)
          } else b
        }.getOrElse(b)
      }

      val newRuntimeConfig = runtimeConfig.copy(
        bundles = runtimeConfig.bundles.map(checkAndupdateBundle),
        fragments = runtimeConfig.fragments.map { f =>
          f.copy(bundles = f.bundles.map(checkAndupdateBundle))
        }
      )

      if (runtimeConfig != newRuntimeConfig) {
        println("Updating config file: " + configFile)
        ConfigWriter.write(RuntimeConfig.toConfig(newRuntimeConfig), configFile, None)
      }
    }

  }

}

