package blended.updater.tools.configbuilder

import java.io.File

import com.typesafe.config.ConfigFactory

import blended.updater.config.ConfigWriter
import blended.updater.config.RuntimeConfig
import de.tototec.cmdoption.CmdOption
import de.tototec.cmdoption.CmdlineParser

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

