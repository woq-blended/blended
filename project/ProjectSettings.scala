import sbt._
import sbt.Keys._

case class ProjectSettings(
  prjName: String,
  desc: String
) {

  def libDependencies : Seq[ModuleID] = Seq()

  def bundle = BlendedBundle(
    bundleSymbolicName = prjName,
    exportPackage = Seq(prjName),
    privatePackage = Seq(prjName + ".internal")
  )

  def settings : Seq[Setting[_]] =  Seq(
    name := prjName,
    description := desc,
    libraryDependencies ++= libDependencies,
  ) ++
    bundle.osgiSettings

}
