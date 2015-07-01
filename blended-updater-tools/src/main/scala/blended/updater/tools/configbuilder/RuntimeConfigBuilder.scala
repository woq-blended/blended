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
import blended.updater.config.FragmentConfig
import blended.updater.config.BundleConfig
import blended.updater.config.Artifact

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
        FragmentConfig.read(c.toConfig()).get
      }
    }

    val configFile = new File(options.configFile).getAbsoluteFile()
    val dir = configFile.getParentFile()
    val config = ConfigFactory.parseFile(configFile).resolve()
    var runtimeConfig = RuntimeConfig.read(config, fragments).get

    if (options.check) {
      val issues = RuntimeConfig.validate(
        dir,
        runtimeConfig,
        includeResourceArchives = true,
        explodedResourceArchives = false
      )
      if (!issues.isEmpty) {
        println(issues.mkString("\n"))
        sys.exit(1)
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
        sys.exit(1)
      }
    }

    if (options.updateChecksums) {
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

      val newRuntimeConfig = runtimeConfig.copy(
        bundles = runtimeConfig.bundles.map(checkAndUpdateBundle),
        fragments = runtimeConfig.fragments.map { f =>
          f.copy(bundles = f.bundles.map(checkAndUpdateBundle))
        },
        resources = runtimeConfig.resources.map(checkAndUpdateResource)
      )

      if (runtimeConfig != newRuntimeConfig) {
        println("Updating config file: " + configFile)
        ConfigWriter.write(RuntimeConfig.toConfig(newRuntimeConfig), configFile, None)
      }
    }

  }

}

