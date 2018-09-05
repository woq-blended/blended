import sbt._
import sbt.Keys._

case class ProjectSettings(
  prjName: String,
  desc: String,
  osgi : Boolean = true,
  publish : Boolean = true
) {

  def libDependencies : Seq[ModuleID] = Seq()

  def bundle : BlendedBundle = BlendedBundle(
    bundleSymbolicName = prjName,
    exportPackage = Seq(prjName),
    privatePackage = Seq(prjName + ".internal")
  )

  final protected def sbtBundle : Option[BlendedBundle] = if (osgi) {
    Some(bundle)
  } else {
    None
  }

  def settings : Seq[Setting[_]] =  {
    val osgiSettings : Seq[Setting[_]] = sbtBundle.toSeq.flatMap { _.osgiSettings }

    Seq(
      name := prjName,
      description := desc,
      libraryDependencies ++= libDependencies,
      Test/javaOptions += ("-DprojectTestOutput=" + target.value / s"scala-${scalaBinaryVersion.value}" / "test-classes"),
      Test/fork := true,
      Compile/unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "binaryResources",
      Test/unmanagedResourceDirectories += baseDirectory.value / "src" / "test" / "binaryResources"
    ) ++ osgiSettings ++ (
      if (publish) PublishConfg.doPublish else PublishConfg.noPublish
    )
  }
}
