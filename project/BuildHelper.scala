import com.typesafe.sbt.osgi.SbtOsgi
import com.typesafe.sbt.osgi.SbtOsgi.autoImport._
import sbt._
import sbt.Keys._

object BuildHelper {

  lazy val defaultSettings : Seq[Def.SettingsDefinition] = Seq(
    organization := BlendedVersions.blendedGroupId,
    version := BlendedVersions.blended,

    scalaVersion := BlendedVersions.scala,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-Ywarn-nullary-override"),
    sourcesInBase := false
  )

  def blendedOsgiProject(
    pName: String,
    pDescription: Option[String] = None,
    deps : Seq[ModuleID] = Seq.empty,
    exports : Seq[String] = Seq.empty,
    imports : Seq[String] = Seq.empty
  ) : Project = {
    blendedProject(
      pName = pName,
      pDescription = pDescription,
      deps = deps
    )
    .settings(osgiSettings)
    .settings(
      BuildHelper.bundleSettings(
        symbolicName = pName,
        exports = exports
      ):_*
    )
    .enablePlugins(SbtOsgi)
  }

  def blendedProject(
    pName: String,
    pDescription: Option[String] = None,
    deps : Seq[ModuleID] = Seq.empty
  ) : Project = {
    sbt.Project.apply(pName.split("\\.").map(s => s.toLowerCase.capitalize).mkString, file(pName))
      .settings(defaultSettings:_*)
      .settings(
        name := pName,
        description := pDescription.getOrElse(pName),

        libraryDependencies ++= deps
      )

  }

  def bundleSettings(
    symbolicName : String,
    exports: Seq[String] = Seq.empty,
    imports: Seq[String] = Seq.empty
  ) : Seq[Def.SettingsDefinition] = {

    val scalaRange : String => String = { sv =>
      val v = sv.split("\\.").take(2).mkString(".")
      "scala.*;version=\"[" + v + "," + v + ".50)\""
    }

    Seq(
      OsgiKeys.bundleSymbolicName := symbolicName,
      OsgiKeys.bundleVersion := BlendedVersions.blended,
      OsgiKeys.exportPackage := exports.map(symbolicName + "." + _),
      OsgiKeys.importPackage := Seq(scalaRange(scalaVersion.value)) ++ imports ++ Seq("*")
    )
  }

}
