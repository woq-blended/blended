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

  val osgi: Boolean = true
  val publish: Boolean = true

  val libDeps: Seq[ModuleID] = Seq.empty
  val extraPlugins: Seq[AutoPlugin] = Seq.empty

  val projectFactory : () => Project = { () =>
    val name = prjName.split("[.]").foldLeft("") { (name, next) =>
      if (name.isEmpty) {
        next
      } else {
        name + next.capitalize
      }
    }

    Project(name, file(prjName))
  }

  lazy val defaultBundle : BlendedBundle = BlendedBundle(
    bundleSymbolicName = prjName,
    exportPackage = Seq(prjName),
    privatePackage = Seq(s"${prjName}.internal.*")
  )

  lazy val bundle = defaultBundle

  lazy val sbtBundle: Option[BlendedBundle] =
    if (osgi) {
      Some(bundle)
    } else {
      None
    }

  val defaultSettings : Seq[Setting[_]] = {

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

  val settings = defaultSettings

  val plugins: Seq[AutoPlugin] = extraPlugins ++ (if (osgi) Seq(SbtOsgi) else Seq())

  // creates the project and apply settings and plugins
  def baseProject: Project = {
    projectFactory
      .apply()
      .settings(settings)
      .enablePlugins(plugins: _*)
  }

}
