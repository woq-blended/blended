package blended.updater.tools.configbuilder

import java.io.{File, PrintStream}

import blended.updater.config._
import com.typesafe.config.{ConfigFactory, ConfigParseOptions}
import de.tototec.cmdoption.{CmdOption, CmdlineParser}

import scala.util.Success

object FeatureBuilder {

  class CmdlineCommon {
    @CmdOption(names = Array("-h", "--help"), isHelp = true, description = "Show this help")
    var help: Boolean = false

    @CmdOption(
      names = Array("--debug"),
      description = "Show debug information and stack traces"
    )
    var debug: Boolean = false

  }

  class Cmdline {

    @CmdOption(names = Array("-w", "--work-dir"), args = Array("dir"))
    def setOutputDir(outputDir: String): Unit = this.outputDir = Option(outputDir)
    var outputDir: Option[String] = None

    @CmdOption(names = Array("-m", "--maven-url", "--maven-dir"), args = Array("URL"), maxCount = -1)
    def addMavenUrl(mavenDir: String): Unit = this.mavenUrl ++= Seq(mavenDir)
    var mavenUrl: Seq[String] = Seq()

    @CmdOption(names = Array("--maven-artifact"), args = Array("GAV", "file"), maxCount = -1)
    def addMavenDir(gav: String, file: String): Unit = this.mavenArtifacts ++= Seq(gav -> file)
    var mavenArtifacts: Seq[(String, String)] = Seq()

    @CmdOption(names = Array("-d", "--download-missing"))
    var downloadMissing: Boolean = false

    @CmdOption(names = Array("-D", "--discard-invalid"))
    var discardInvalid: Boolean = false

    @CmdOption(names = Array("-u", "--update-checksums"))
    var updateChecksums: Boolean = false

    //    @CmdOption(names = Array("-c", "--check"))
    //    var check: Boolean = false

    @CmdOption(names = Array("-f", "--file"), args = Array("file"), description = "Feature file to read", minCount = 1)
    var featureFiles: String = _

    @CmdOption(names = Array("-o", "--output"), args = Array("file"), description = "Feature file to write")
    def setOutputFile(file: String): Unit = outputFile = Option(file)
    var outputFile: Option[String] = None

    override def toString: String =
      getClass().getSimpleName +
        "(outputDir=" + outputDir +
        ",mavenDir=" + mavenUrl +
        ",mavenArtifacts=" + mavenArtifacts +
        ",downloadMissing=" + downloadMissing +
        ",discardInvalid=" + discardInvalid +
        ",unpdateChecksums=" + updateChecksums +
        ",featureFiles=" + featureFiles +
        ")"
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
    val cmdlineCommon = new CmdlineCommon()
    val cmdline = new Cmdline()

    val cp = new CmdlineParser(cmdlineCommon, cmdline)
    cp.parse(args: _*)
    if (cmdlineCommon.help) {
      cp.usage()
      return
    }

    if (cmdlineCommon.debug) {
      Console.err.println(s"Config: $cmdline")
    }

    run(cmdline, cmdlineCommon.debug)
  }

  def run(cmdline: Cmdline, debug: Boolean): Unit = {
    val workDir = new File(cmdline.outputDir.getOrElse("/tmp")).getAbsoluteFile()

    val file = new File(cmdline.featureFiles)
    val config = ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()

    val feature = FeatureConfigCompanion.read(config).get
    Console.err.println(s"Processing feature: ${feature.repoUrl}-${feature.name}")

    val bundles = feature.bundles
    val mvnUrls = cmdline.mavenUrl // .map { d => new File(d).getAbsoluteFile().toURI().toString() }
    val mvnGavs = cmdline.mavenArtifacts
      .map {
        case (gav, file) => MvnGav.parse(gav) -> file
      }
      .collect {
        case (Success(gav), file) => gav -> file
      }

    val bundleFiles = bundles.map { bundle =>
      bundle -> new File(workDir, Profile.resolveFileName(bundle.url).get)
    }

    bundleFiles.map {
      case (bundle, bundleFile) =>
        if (bundleFile.exists()) {
          val digest = ProfileCompanion.digestFile(bundleFile)
          if (bundle.sha1Sum.isDefined && bundle.sha1Sum != digest) {
            if (debug) {
              Console.err.println(
                s"Bundle file [$bundleFile] has invalid checksum: [$digest], expected: [${bundle.sha1Sum}]")
            }
            if (cmdline.discardInvalid) {
              if (debug) {
                Console.err.println(s"Deleting bundle file [$bundleFile]")
              }
              bundleFile.delete()
            }
          }
        }

        if (!bundleFile.exists() && cmdline.downloadMissing) {
          // lookup in GAV
          val directUrl = MvnGavSupport.downloadUrls(mvnGavs, bundle.artifact, debug)

          val urls =
            if (directUrl.isDefined) directUrl.toSeq
            else mvnUrls.map(url => Profile.resolveBundleUrl(bundle.url, Option(url)).get)
          urls.find { url =>
            Console.err.println(s"Downloading ${bundleFile.getName()} from [$url]")
            ProfileCompanion.download(url, bundleFile).isSuccess
          } getOrElse {
            val msg = s"Could not download [${bundleFile.getName()}] from: [$urls]"
            Console.err.println(msg)
            sys.error(msg)
          }
        }
    }

    val newFeature = if (cmdline.updateChecksums) {
      val newBundles = bundleFiles.map {
        case (bundle, bundleFile) =>
          if (bundleFile.exists()) {
            val digest = ProfileCompanion.digestFile(bundleFile)
            bundle.copy(artifact = bundle.artifact.copy(sha1Sum = digest))
          } else {
            val msg = s"Cannot update checksum of missing bundle file: [${bundleFile.getName()}]"
            Console.err.println(msg)
            sys.error(msg)
          }
      }
      // config.withValue("bundles", ConfigValueFactory.fromIterable(newBundles.map(c => BundleConfig.toConfig(c).root().unwrapped()).asJava))
      feature.copy(bundles = newBundles)
    } else feature

    if (cmdline.outputFile.isDefined) {
      ConfigWriter.write(FeatureConfigCompanion.toConfig(newFeature), new File(cmdline.outputFile.get), None)
    } else {
      val ps = new PrintStream(Console.out)
      ConfigWriter.write(FeatureConfigCompanion.toConfig(newFeature), ps, None)
    }
  }
}
