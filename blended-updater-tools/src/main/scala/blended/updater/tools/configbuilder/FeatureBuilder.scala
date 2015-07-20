package blended.updater.tools.configbuilder

import de.tototec.cmdoption.CmdOption
import de.tototec.cmdoption.CmdlineParser
import scala.util.control.NonFatal
import java.io.File
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.util.Try
import blended.updater.config.FeatureConfig
import blended.updater.config.RuntimeConfig
import de.tototec.cmdoption.internal.LoggerFactory
import blended.updater.config.BundleConfig
import com.typesafe.config.ConfigValueFactory
import scala.collection.JavaConverters._
import blended.updater.config.ConfigWriter
import java.io.PrintStream

object FeatureBuilder {

  class CmdlineCommon {
    @CmdOption(names = Array("-h", "--help"), isHelp = true,
      description = "Show this help")
    var help: Boolean = false

    @CmdOption(names = Array("--debug"),
      description = "Show debug information and stack traces")
    var debug: Boolean = false

  }

  class Cmdline {

    @CmdOption(names = Array("-w", "--work-dir"), args = Array("dir"))
    def setOutputDir(outputDir: String) = this.outputDir = Option(outputDir)
    var outputDir: Option[String] = None

    @CmdOption(names = Array("-m", "--maven-dir"), args = Array("dir"), maxCount = -1)
    def addMavenDir(mavenDir: String) = this.mavenDir ++= Seq(mavenDir)
    var mavenDir: Seq[String] = Seq()

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

    override def toString: String = getClass().getSimpleName +
      "(outputDir=" + outputDir +
      ",mavenDir=" + mavenDir +
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

    if (cmdlineCommon.debug) Console.err.println(s"Config: ${cmdline}")

    run(cmdline, cmdlineCommon.debug)
  }

  def run(cmdline: Cmdline, debug: Boolean): Unit = {
    val workDir = new File(cmdline.outputDir.getOrElse("/tmp")).getAbsoluteFile()

    val file = new File(cmdline.featureFiles)
    val config = ConfigFactory.parseFile(file).resolve()

    val feature = FeatureConfig.read(config).get
    Console.err.println(s"Processing feature: ${feature.name} ${feature.version}")

    val bundles = feature.bundles
    val mvnUrls = cmdline.mavenDir // .map { d => new File(d).getAbsoluteFile().toURI().toString() }

    val bundleFiles = bundles.map { bundle =>
      bundle -> new File(workDir, RuntimeConfig.resolveFileName(bundle.url).get)
    }

    bundleFiles.map {
      case (bundle, bundleFile) =>

        if (bundleFile.exists()) {
          val digest = RuntimeConfig.digestFile(bundleFile)
          if (bundle.sha1Sum.isDefined && bundle.sha1Sum != digest) {
            if (debug) Console.err.println(s"Bundle file ${bundleFile} has invalid checksum: ${digest}, expected: ${bundle.sha1Sum}")
            if (cmdline.discardInvalid) {
              if (debug) Console.err.println(s"Deleting bundle file ${bundleFile}")
              bundleFile.delete()
            }
          }
        }

        lazy val urls = mvnUrls.map(url => RuntimeConfig.resolveBundleUrl(bundle.url, Option(url)).get)

        if (!bundleFile.exists() && cmdline.downloadMissing) {
          urls.find { url =>
            Console.err.println(s"Downloading ${bundleFile.getName()} from ${url}")
            RuntimeConfig.download(url, bundleFile).isSuccess
          } getOrElse {
            val msg = s"Could not download ${bundleFile.getName()} from: ${urls}"
            Console.err.println(msg)
            sys.error(msg)
          }
        }
    }

    val newFeature = if (cmdline.updateChecksums) {
      val newBundles = bundleFiles.map {
        case (bundle, bundleFile) =>
          if (bundleFile.exists()) {
            val digest = RuntimeConfig.digestFile(bundleFile)
            bundle.copy(artifact = bundle.artifact.copy(sha1Sum = digest))
          } else {
            val msg = s"Cannot update checksum of missing bundle file: ${bundleFile.getName()}"
            Console.err.println(msg)
            sys.error(msg)
          }
      }
      // config.withValue("bundles", ConfigValueFactory.fromIterable(newBundles.map(c => BundleConfig.toConfig(c).root().unwrapped()).asJava))
      feature.copy(bundles = newBundles)
    } else feature

    if (cmdline.outputFile.isDefined) {
      ConfigWriter.write(FeatureConfig.toConfig(newFeature), new File(cmdline.outputFile.get), None)
    } else {
      val ps = new PrintStream(Console.out)
      ConfigWriter.write(FeatureConfig.toConfig(newFeature), ps, None)
    }
  }

}