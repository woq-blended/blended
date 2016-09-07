package blended.updater.tools.configbuilder

import java.io.File

import blended.updater.config._
import com.typesafe.config.{ConfigFactory, ConfigParseOptions}
import de.tototec.cmdoption.{CmdOption, CmdlineParser}

import scala.collection.immutable._
import scala.util.Failure
import scala.util.Try
import java.io.PrintWriter

import com.typesafe.config.ConfigParseOptions

import scala.util.Success

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
    def addFeatureRepo(repo: String): Unit = featureRepos ++= Seq(repo)
    var featureRepos: Seq[String] = Seq()

    @CmdOption(names = Array("-m", "--maven-url"), args = Array("url"), maxCount = -1)
    def addMavenUrl(mavenUrl: String) = this.mavenUrls ++= Seq(mavenUrl)
    var mavenUrls: Seq[String] = Seq()

    @CmdOption(names = Array("--debug"))
    var debug: Boolean = false

    @CmdOption(names = Array("--maven-artifact"), args = Array("GAV", "file"), maxCount = -1)
    def addMavenDir(gav: String, file: String) = this.mavenArtifacts ++= Seq(gav -> file)
    var mavenArtifacts: Seq[(String, String)] = Seq()

  }

  def main(args: Array[String]): Unit = {
    try {
      run(args)
      sys.exit(0)
    } catch {
      case e: Throwable =>
        Console.err.println(s"An error occurred: ${e.getMessage()}")
        sys.exit(1)
    }
  }

  def run(args: Array[String]): Unit = {
    println(s"RuntimeConfigBuilder: ${args.mkString(" ")}")

    val options = new CmdOptions()

    val cp = new CmdlineParser(options)
    cp.parse(args: _*)
    if (options.help) {
      cp.usage()
      return
    }

    val debug = options.debug

    if (options.configFile.isEmpty()) sys.error("No config file given")

    val mvnGavs = options.mavenArtifacts.map {
      case (gav, file) => MvnGav.parse(gav) -> file
    }.collect {
      case (Success(gav), file) => gav -> file
    }

    // read feature repo files
    val features = options.featureRepos.map { fileName =>
      val featureConfig = ConfigFactory.parseFile(new File(fileName), ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
      FeatureConfigCompanion.read(featureConfig).get
    }

    val configFile = new File(options.configFile).getAbsoluteFile()
    val outFile = Option(options.outFile.trim())
      .filter(!_.isEmpty())
      .orElse(if (options.inPlace) Option(configFile.getPath()) else None)
      .map(new File(_).getAbsoluteFile())

    val dir = outFile.flatMap(f => Option(f.getParentFile())).getOrElse(configFile.getParentFile())
    val config = ConfigFactory.parseFile(configFile, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
    val unresolvedRuntimeConfig = RuntimeConfigCompanion.read(config).get
    //    val unresolvedLocalRuntimeConfig = LocalRuntimeConfig(unresolvedRuntimeConfig, dir)

    val resolved = FeatureResolver.resolve(unresolvedRuntimeConfig, features).get
    if (debug) Console.err.println("runtime config with resolved features: " + resolved)

    val localRuntimeConfig = LocalRuntimeConfig(resolved, dir)

    if (options.check) {
      val issues = localRuntimeConfig.validate(
        includeResourceArchives = true,
        explodedResourceArchives = false,
        checkPropertiesFile = false
      )
      if (!issues.isEmpty) {
        sys.error(issues.mkString("\n"))
      }
    }

    lazy val mvnUrls = resolved.runtimeConfig.properties.get(RuntimeConfig.Properties.MVN_REPO).toSeq ++ options.mavenUrls
    if (debug) Console.err.println(s"Maven URLs: $mvnUrls")

    def downloadUrls(b: Artifact): Seq[String] = {
      val directUrl = MvnGavSupport.downloadUrls(mvnGavs, b, debug)
      directUrl.map(Seq(_)).getOrElse(mvnUrls.flatMap(baseUrl => RuntimeConfig.resolveBundleUrl(b.url, Option(baseUrl)).toOption).to[Seq])
    }

    if (options.downloadMissing) {

      val files = resolved.allBundles.distinct.map { b =>
        RuntimeConfigCompanion.bundleLocation(b, dir) -> downloadUrls(b.artifact)
      } ++ resolved.runtimeConfig.resources.map(r =>
        RuntimeConfigCompanion.resourceArchiveLocation(r, dir) -> downloadUrls(r)
      )

      val states = files.par.map {
        case (file, urls) =>
          if (!file.exists()) {
            println(s"Downloading: ${file}")
            urls.find { url =>
              Console.err.println(s"Downloading ${file.getName()} from $url")
              RuntimeConfigCompanion.download(url, file).isSuccess
            }.map { url => file -> Try(file)
            }.getOrElse {
              val msg = s"Could not download ${file.getName()} from: $urls"
              Console.err.println(msg)
              sys.error(msg)
            }
          } else file -> Try(file)
      }.seq

      val issues = states.collect {
        case (file, Failure(e)) =>
          Console.err.println(s"Could not download: $file (${e.getClass.getSimpleName()}: ${e.getMessage()})")
          e
      }
      if (!issues.isEmpty) {
        sys.error(issues.mkString("\n"))
      }
    }

    val newRuntimeConfig = if (options.updateChecksums) {
      var checkedFiles: Map[File, String] = Map()
      def checkAndUpdate(file: File, r: Artifact): Artifact = {
        checkedFiles.get(file).orElse(RuntimeConfigCompanion.digestFile(file)).map { checksum =>
          checkedFiles += file -> checksum
          if (r.sha1Sum != Option(checksum)) {
            println(s"${if (r.sha1Sum.isDefined) "Updating" else "Creating"} checksum for: ${r.fileName.getOrElse(RuntimeConfig.resolveFileName(r.url).get)}")
            r.copy(sha1Sum = Option(checksum))
          } else r
        }.getOrElse(r)
      }

      def checkAndUpdateResource(a: Artifact): Artifact =
        checkAndUpdate(localRuntimeConfig.resourceArchiveLocation(a), a)

      def checkAndUpdateBundle(b: BundleConfig): BundleConfig =
        b.copy(artifact = checkAndUpdate(localRuntimeConfig.bundleLocation(b), b.artifact))

      def checkAndUpdateFeatures(f: FeatureConfig): FeatureConfig =
        f.copy(bundles = f.bundles.map(checkAndUpdateBundle))

      ResolvedRuntimeConfig(
        resolved.runtimeConfig.copy(
          bundles = resolved.runtimeConfig.bundles.map(checkAndUpdateBundle),
          resolvedFeatures = resolved.allReferencedFeatures.map(checkAndUpdateFeatures),
          resources = resolved.runtimeConfig.resources.map(checkAndUpdateResource)
        )
      ).runtimeConfig
    } else resolved.runtimeConfig

    outFile match {
      case None =>
        ConfigWriter.write(RuntimeConfigCompanion.toConfig(newRuntimeConfig), Console.out, None)
      case Some(f) =>
        Console.err.println("Writing config file: " + configFile)
        ConfigWriter.write(RuntimeConfigCompanion.toConfig(newRuntimeConfig), f, None)
    }

  }

}
