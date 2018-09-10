import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.SbtOsgi

trait ProjectHelper {
  val project: Project
}

abstract class ProjectSettings(
  val prjName : String,
  val desc : String
) {

  def osgi: Boolean = true
  def publish: Boolean = true

  def libDeps: Seq[ModuleID] = Seq.empty
  def extraPlugins: Seq[AutoPlugin] = Seq.empty

  def projectFactory : () => Project = { () =>
    val name = prjName.split("[.]").foldLeft("") { (name, next) =>
      if (name.isEmpty) {
        next
      } else {
        name + next.capitalize
      }
    }

    Project(name, file(prjName))
  }

  def defaultBundle : BlendedBundle = BlendedBundle(
    bundleSymbolicName = prjName,
    exportPackage = Seq(prjName),
    privatePackage = Seq(s"${prjName}.internal.*")
  )

  def bundle = defaultBundle

  def sbtBundle: Option[BlendedBundle] =
    if (osgi) {
      Some(bundle)
    } else {
      None
    }

  def defaultSettings : Seq[Setting[_]] = {

    val osgiSettings: Seq[Setting[_]] = sbtBundle.toSeq.flatMap(_.osgiSettings)

    Seq(
      name := prjName,
      description := desc,
      libraryDependencies ++= libDeps,
      Test / javaOptions += ("-DprojectTestOutput=" + target.value / s"scala-${scalaBinaryVersion.value}" / "test-classes"),
      Test / fork := true,
      Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "binaryResources",
      Test / unmanagedResourceDirectories += baseDirectory.value / "src" / "test" / "binaryResources"
    ) ++ osgiSettings ++ (
      if (publish) PublishConfg.doPublish else PublishConfg.noPublish
    )
  }

  def settings = defaultSettings

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
