package blended.updater.tools.configbuilder

import java.io.File
import com.typesafe.config.ConfigFactory
import blended.updater.config.ConfigWriter
import blended.updater.config.RuntimeConfig
import de.tototec.cmdoption.CmdOption
import de.tototec.cmdoption.CmdlineParser
import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.util.Failure
import scala.util.Try
import blended.updater.config.FeatureConfig
import blended.updater.config.BundleConfig
import blended.updater.config.Artifact
import java.io.PrintWriter

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

    @CmdOption(names = Array("-f"), args = Array("configfile"), description = "Read the configuration from file {0}")
    var configFile: String = ""

    @CmdOption(names = Array("-o"), args = Array("outfile"), description = "Write the updated config file to {0}",
      conflictsWith = Array("-i")
    )
    var outFile: String = ""

    @CmdOption(names = Array("-i", "--in-place"),
      description = "Modifiy the input file (-o) instead of writing to the output file",
      requires = Array("-f"),
      conflictsWith = Array("-o")
    )
    var inPlace: Boolean = false

    @CmdOption(names = Array("-r", "--feature-repo"), args = Array("featurefile"),
      description = "Lookup additional feature configuration(s) from file {0}",
      maxCount = -1
    )
    def addFeatureRepo(repo: String): Unit = featureRepos +:= repo
    var featureRepos: Seq[String] = Seq()
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

    if (options.configFile.isEmpty()) sys.error("No config file given")

    // read feature repo files
    val features = options.featureRepos.flatMap { fileName =>
      val repoConfig = ConfigFactory.parseFile(new File(fileName)).resolve()
      repoConfig.getObjectList("fragments").asScala.map { c =>
        FeatureConfig.read(c.toConfig()).get
      }
    }

    val configFile = new File(options.configFile).getAbsoluteFile()
    val dir = configFile.getParentFile()
    val config = ConfigFactory.parseFile(configFile).resolve()
    val runtimeConfig = RuntimeConfig.read(config, features).get

    val resolvedRuntimeConfig = FragmentResolver.resolve(runtimeConfig, features)
    println("runtime config with resolved features: " + resolvedRuntimeConfig)

    val outFile = Option(options.outFile.trim())
      .filter(!_.isEmpty())
      .orElse(if (options.inPlace) Option(configFile.getPath()) else None)
      .map(new File(_).getAbsoluteFile())

    var exitCode = 0

    if (options.check) {
      val issues = RuntimeConfig.validate(
        dir,
        runtimeConfig,
        includeResourceArchives = true,
        explodedResourceArchives = false
      )
      if (!issues.isEmpty) {
        Console.err.println(issues.mkString("\n"))
        exitCode = 1
      }
    }

    if (options.downloadMissing) {

      val files = runtimeConfig.allBundles.map(b =>
        runtimeConfig.bundleLocation(b, dir) -> runtimeConfig.resolveBundleUrl(b.url).getOrElse(b.url)
      ) ++
        runtimeConfig.resources.map(r =>
          runtimeConfig.resourceArchiveLocation(r, dir) -> runtimeConfig.resolveBundleUrl(r.url).getOrElse(r.url)
        )

      val states = files.par.map {
        case (file, url) =>
          if (!file.exists()) {
            println(s"Downloading: ${file}")
            file -> RuntimeConfig.download(url, file)
          } else file -> Try(file)
      }.seq

      val issues = states.collect {
        case (file, Failure(e)) =>
          Console.err.println(s"Could not download: ${file} (${e.getClass.getSimpleName()}: ${e.getMessage()})")
          e
      }
      if (!issues.isEmpty) {
        exitCode = 1
      }
    }

    val newRuntimeConfig = if (options.updateChecksums) {
      def checkAndUpdate(file: File, r: Artifact): Artifact = {
        RuntimeConfig.digestFile(file).map { checksum =>
          if (r.sha1Sum != Option(checksum)) {
            println(s"Updating checksum for: ${r.fileName.getOrElse(runtimeConfig.resolveFileName(r.url).get)}")
            r.copy(sha1Sum = Option(checksum))
          } else r
        }.getOrElse(r)
      }

      def checkAndUpdateResource(a: Artifact): Artifact =
        checkAndUpdate(runtimeConfig.resourceArchiveLocation(a, dir), a)

      def checkAndUpdateBundle(b: BundleConfig): BundleConfig =
        b.copy(artifact = checkAndUpdate(runtimeConfig.bundleLocation(b, dir), b.artifact))

      runtimeConfig.copy(
        bundles = runtimeConfig.bundles.map(checkAndUpdateBundle),
        features = runtimeConfig.features.map { f =>
          f.copy(bundles = f.bundles.map(checkAndUpdateBundle))
        },
        resources = runtimeConfig.resources.map(checkAndUpdateResource)
      )
    } else runtimeConfig

    outFile match {
      case None =>
        ConfigWriter.write(RuntimeConfig.toConfig(newRuntimeConfig), Console.out, None)
      case Some(f) =>
        //        if (runtimeConfig != newRuntimeConfig) {
        Console.err.println("Writing config file: " + configFile)
        ConfigWriter.write(RuntimeConfig.toConfig(newRuntimeConfig), f, None)
      //        }
    }

    sys.exit(exitCode)
  }

}

