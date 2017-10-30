import com.typesafe.sbt.osgi.SbtOsgi
import com.typesafe.sbt.osgi.SbtOsgi.autoImport._
import sbt._
import sbt.Keys._

object BuildHelper {

  lazy val defaultSettings : Seq[Def.SettingsDefinition] = Seq(
    organization := BlendedVersions.blendedGroupId,
    homepage := Some(url("https://github.com/woq-blended/blended")),
    version := BlendedVersions.blended,
    licenses += ("Apache 2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    developers := List(
      Developer(id = "andreas", name = "Andreas Gies", email = "andreas@wayofquality.de", url = url("https://github.com/woq-blended/blended")),
      Developer(id = "tobias", name = "Tobias Roeser", email = "tobias.roser@tototec.de", url = url("https://github.com/woq-blended/blended"))
    ),

    crossScalaVersions := Seq(BlendedVersions.scala), //Seq("2.11.11", "2.12.4"),
    scalaVersion := BlendedVersions.scala,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-Ywarn-nullary-override"),
    sourcesInBase := false
  )

  def bundleSettings(
    symbolicName : String,
    exports: Seq[String] = Seq.empty,
    imports: Seq[String] = Seq.empty,
    privates: Seq[String] = Seq.empty
  ) : Seq[Def.SettingsDefinition] = {

    val scalaRange : String => String = { sv =>
      val v = sv.split("\\.").take(2).mkString(".")
      "scala.*;version=\"[" + v + "," + v + ".50)\""
    }

    val mapPkg : String => String = {
      case e if e.isEmpty => symbolicName
      case s if s.startsWith("/") => s.substring(1)
      case s => symbolicName + "." + s
    }

    Seq(
      OsgiKeys.bundleSymbolicName := symbolicName,
      OsgiKeys.bundleVersion := BlendedVersions.blended,
      OsgiKeys.exportPackage := exports.map(mapPkg),
      OsgiKeys.privatePackage := (Seq("internal") ++ privates).map(mapPkg),
      OsgiKeys.importPackage := Seq(scalaRange(scalaVersion.value)) ++ imports ++ Seq("*")
    )
  }

  def blendedOsgiProject(
    pName: String,
    pDescription: Option[String] = None,
    exports : Seq[String] = Seq.empty,
    imports : Seq[String] = Seq.empty,
    privates : Seq[String] = Seq.empty
  ) : Project = {
    blendedProject(
      pName = pName,
      pDescription = pDescription
    )
    .settings(osgiSettings)
    .settings(
      BuildHelper.bundleSettings(
        symbolicName = pName,
        exports = exports,
        privates = privates
      ):_*
    )
    .enablePlugins(SbtOsgi)
  }

  def blendedProject(
    pName: String,
    pDescription: Option[String] = None
  ) : Project = {
    sbt.Project.apply(pName.split("\\.").map(s => s.toLowerCase.capitalize).mkString, file(pName))
      .settings(defaultSettings:_*)
      .settings(
        name := pName,
        description := pDescription.getOrElse(pName)
      )

  }
}
