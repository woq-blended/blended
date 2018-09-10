import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.SbtOsgi

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
  */
class ProjectSettings(
                       val projectName: String,
                       val description: String,
                       deps: Seq[ModuleID] = Seq.empty,
                       osgi: Boolean = true,
                       publish: Boolean = true,
                       adaptBundle: BlendedBundle => BlendedBundle = identity
                     ) {

  def libDeps: Seq[ModuleID] = deps

  def extraPlugins: Seq[AutoPlugin] = Seq.empty

  def projectFactory: () => Project = { () =>
    val name = projectName.split("[.]").foldLeft("") {
      case ("", next) => next
      case (name, next) => name + next.capitalize
    }
    Project(name, file(projectName))
  }

  def defaultBundle: BlendedBundle = BlendedBundle(
    bundleSymbolicName = projectName,
    exportPackage = Seq(projectName),
    privatePackage = Seq(s"${projectName}.internal.*")
  )

  def bundle: BlendedBundle = adaptBundle(defaultBundle)

  def sbtBundle: Option[BlendedBundle] =
    if (osgi) {
      Some(bundle)
    } else {
      None
    }

  def defaultSettings: Seq[Setting[_]] = {

    val osgiSettings: Seq[Setting[_]] = sbtBundle.toSeq.flatMap(_.osgiSettings)

    Seq(
      Keys.name := projectName,
      Keys.description := description,
      Keys.libraryDependencies ++= libDeps,
      Test / javaOptions += ("-DprojectTestOutput=" + target.value / s"scala-${scalaBinaryVersion.value}" / "test-classes"),
      Test / fork := true,
      Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "binaryResources",
      Test / unmanagedResourceDirectories += baseDirectory.value / "src" / "test" / "binaryResources"
    ) ++ osgiSettings ++ (
      if (publish) PublishConfg.doPublish else PublishConfg.noPublish
      )
  }

  def settings: Seq[sbt.Setting[_]] = defaultSettings

  def plugins: Seq[AutoPlugin] = extraPlugins ++ (if (osgi) Seq(SbtOsgi) else Seq())

  // creates the project and apply settings and plugins
  def baseProject: Project = {
    val p = projectFactory
      .apply()
      .settings(settings)
      .enablePlugins(plugins: _*)

    println(p)
    p
  }

}
