import CommonSettings.hasForkAnnotation
import TestLogConfig.autoImport._
import com.typesafe.sbt.osgi.SbtOsgi
import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import sbt.internal.inc.Analysis
import xerial.sbt.Sonatype

trait ProjectFactory {
  val project: Project
}

/**
 * Blended project settings.
 *
 * @param projectName The Project name, also used as Bundle-Name and prefix for package names.
 * @param description The project description, also used as Bundle-Description.
 * @param deps        The project classpath dependencies (exclusive of other blended projects).
 * @param osgi        If `true` this project is packaged as OSGi Bundle.
 * @param publish     If `true`, this projects package will be publish.
 * @param adaptBundle adapt the bundle configuration (used by sbt-osgi)
 * @param projectDir  Optional project directory (use this if not equal to project name)
 */
class ProjectSettings(
  val projectName: String,
  val description: String,
  deps: Seq[ModuleID] = Seq.empty,
  osgi: Boolean = true,
  osgiDefaultImports: Boolean = true,
  publish: Boolean = true,
  adaptBundle: BlendedBundle => BlendedBundle = identity,
  val projectDir: Option[String] = None
) {

  def libDeps: Seq[ModuleID] = deps

  /**
   * Override this method to specify additional plugins for this project.
   */
  def extraPlugins: Seq[AutoPlugin] = Seq.empty

  /**
   * Override this method to customize the creation of this project.
   */
  def projectFactory: () => Project = { () =>
    val name = projectName.split("[.]").foldLeft("") {
      case ("", next) => next
      case (name, next) => name + next.capitalize
    }
    Project(name, file(projectDir.getOrElse(projectName)))
  }

  def defaultBundle: BlendedBundle = BlendedBundle(
    bundleSymbolicName = projectName,
    exportPackage = Seq(projectName),
    privatePackage = Seq(s"${projectName}.internal.*"),
    defaultImports = osgiDefaultImports
  )

  def bundle: BlendedBundle = adaptBundle(defaultBundle)

  def sbtBundle: Option[BlendedBundle] = if (osgi) Some(bundle) else None

  def defaultSettings: Seq[Setting[_]] = CommonSettings() ++ {

    val osgiSettings: Seq[Setting[_]] = sbtBundle.toSeq.flatMap(_.osgiSettings)

    Seq(
      Keys.name := projectName,
      Keys.moduleName := Keys.name.value,
      Keys.description := description,
      Keys.libraryDependencies ++= libDeps,
      Test / javaOptions += ("-DprojectTestOutput=" + (Test / classDirectory).value), 
      Test / fork := true,
      Test / parallelExecution := false,
      Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "binaryResources",
      Test / unmanagedResourceDirectories += baseDirectory.value / "src" / "test" / "binaryResources",

      Test / testlogDirectory := (Global/testlogDirectory).value,
      Test / testlogLogToConsole := false,
      Test / testlogLogToFile := true,

      Test / resourceGenerators += (Test / testlogCreateConfig).taskValue,

      // inspired by : https://chariotsolutions.com/blog/post/sbt-group-annotated-tests-run-forked-jvms 
      Test / testGrouping := {

        val log = streams.value.log

        val options = (Test/javaOptions).value.toVector

        val annotatedTestNames : Seq[String] = (Test/compile).value.asInstanceOf[Analysis]
          .apis.internal.values.filter(hasForkAnnotation).map(_.name()).toSeq

        val (forkedTests, otherTests) = (Test / definedTests).value.partition{ t =>
          annotatedTestNames.contains(t.name)
        }

        val combined : Tests.Group = new Group(
          name = "Combined",
          tests = otherTests,
          runPolicy = SubProcess(config = ForkOptions.apply().withRunJVMOptions(options))
        )

        val forked : Seq[Tests.Group] = forkedTests.map { t =>
          new Group(
            name = t.name,
            tests = Seq(t),
            runPolicy = SubProcess(config = ForkOptions.apply().withRunJVMOptions(options))
          )
        }

        if (forkedTests.nonEmpty) {
          log.info(s"Forking extra JVM for test [${annotatedTestNames.mkString(",")}]")
        }

        forked ++ Seq(combined)
      },

    ) ++ osgiSettings ++ (
        // We need to explicitly load the rb settings again to
        // make sure the OSGi package is post-processed:
        ReproducibleBuildsPlugin.projectSettings
      ) ++ (
          if (publish) PublishConfig.doPublish else PublishConfig.noPublish
        )
  }

  def settings: Seq[sbt.Setting[_]] =  defaultSettings

  def plugins: Seq[AutoPlugin] = extraPlugins ++
    Seq(ReproducibleBuildsPlugin) ++
    Seq(TestLogConfig) ++
    (if (publish) Seq(Sonatype) else Seq()) ++ 
    (if (osgi) Seq(SbtOsgi) else Seq())

  // creates the project and apply settings and plugins
  def baseProject: Project = projectFactory
    .apply()
    .settings(settings)
    .enablePlugins(plugins: _*)

}
