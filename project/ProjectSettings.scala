import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.SbtOsgi

case class ProjectSettings(
  prjName: String,
  desc: String,
  osgi: Boolean = true,
  publish: Boolean = true,
  libDeps: Seq[ModuleID] = Seq.empty,
  customProjectFactory: Boolean = false,
  extraPlugins: Seq[AutoPlugin] = Seq.empty
) {

  /**
    * Dependencies to other libraries (Maven, Ivy).
    */
  def libDependencies: Seq[ModuleID] = libDeps

  def bundle: BlendedBundle = BlendedBundle(
    bundleSymbolicName = prjName,
    exportPackage = Seq(prjName),
    privatePackage = Seq(s"${prjName}.internal.*")
  )

  protected final def sbtBundle: Option[BlendedBundle] =
    if (osgi) {
      Some(bundle)
    } else {
      None
    }

  def settings: Seq[Setting[_]] = {
    val osgiSettings: Seq[Setting[_]] = sbtBundle.toSeq.flatMap(_.osgiSettings)

    Seq(
      name := prjName,
      description := desc,
      libraryDependencies ++= libDependencies,
      Test / javaOptions += ("-DprojectTestOutput=" + target.value / s"scala-${scalaBinaryVersion.value}" / "test-classes"),
      Test / fork := true,
      Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "binaryResources",
      Test / unmanagedResourceDirectories += baseDirectory.value / "src" / "test" / "binaryResources"
    ) ++ osgiSettings ++ (
      if (publish) PublishConfg.doPublish else PublishConfg.noPublish
    )
  }

  var projectFactory: Option[() => Project] =
    if (customProjectFactory) None
    else
      Some { () =>
        // make camelCase name
        val name = prjName.split("[.]").foldLeft("") { (name, next) =>
          if (name.isEmpty) next
          else name + next.substring(0, 1).toUpperCase() + next.substring(1)
        }
        Project(name, file(prjName))
      }

  // creates the project and apply settings and plugins
  lazy val project: Project = {
    val plugins: Seq[AutoPlugin] = extraPlugins ++ (if (osgi) Seq(SbtOsgi) else Seq())
    projectFactory
      .getOrElse(sys.error(s"Custom project factory not initialized for ${prjName}"))
      .apply()
      .settings(settings)
      .enablePlugins(plugins: _*)
  }

}
