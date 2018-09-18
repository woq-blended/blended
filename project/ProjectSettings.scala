import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.SbtOsgi
import TestLogConfig.autoImport._
//import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin

trait ProjectHelper {
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
 * @param projectDir Optional project directory (use this if not equal to project name)
 */
class ProjectSettings(
  val projectName: String,
  val description: String,
  deps: Seq[ModuleID] = Seq.empty,
  osgi: Boolean = true,
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
    privatePackage = Seq(s"${projectName}.internal.*")
  )

  def bundle: BlendedBundle = adaptBundle(defaultBundle)

  def sbtBundle: Option[BlendedBundle] = if (osgi) Some(bundle) else None

  def defaultSettings: Seq[Setting[_]] = {

    val osgiSettings: Seq[Setting[_]] = sbtBundle.toSeq.flatMap(_.osgiSettings)

    Seq(
      Keys.name := projectName,
      Keys.moduleName := Keys.name.value,
      Keys.description := description,
      Keys.libraryDependencies ++= libDeps,
      Test / javaOptions += ("-DprojectTestOutput=" + target.value / s"scala-${scalaBinaryVersion.value}" / "test-classes"),
      Test / fork := true,
      Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "binaryResources",
      Test / unmanagedResourceDirectories += baseDirectory.value / "src" / "test" / "binaryResources",

      Test / testlogLogToConsole := false,
      Test / testlogLogToFile := true,

      Test / resourceGenerators += (Test / testlogCreateConfig).taskValue

    ) ++ osgiSettings ++ (
        // We need to explicitly load the rb settings again to
        // make sure the OSGi package is post-processed:
        //        ReproducibleBuildsPlugin.projectSettings
        //      ) ++ (
        if (publish) PublishConfig.doPublish else PublishConfig.noPublish
      )
  }

  def settings: Seq[sbt.Setting[_]] = defaultSettings

  def plugins: Seq[AutoPlugin] = extraPlugins ++
    //    Seq(ReproducibleBuildsPlugin) ++
    (if (osgi) Seq(SbtOsgi, TestLogConfig) else Seq(TestLogConfig))

  // creates the project and apply settings and plugins
  def baseProject: Project = projectFactory
    .apply()
    .settings(settings)
    .enablePlugins(plugins: _*)

}
