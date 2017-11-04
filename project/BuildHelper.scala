import com.typesafe.sbt.osgi.SbtOsgi.autoImport._
import sbt._
import sbt.Keys._
import blended.sbt.BlendedPlugin.autoImport._

object BuildHelper {

  def mapPkg(symbolicName : String, export: String) : String = { export match {
    case e if e.isEmpty => symbolicName
    case s if s.startsWith("/") => s.substring(1)
    case s => symbolicName + "." + s
  }}

  def bundleSettings(
    exportPkgs: Seq[String] = Seq.empty,
    importPkgs: Seq[String] = Seq.empty,
    privatePkgs: Seq[String] = Seq.empty
  ): Seq[Setting[_]] = Seq(
    OsgiKeys.bundleSymbolicName := name.value,
    OsgiKeys.bundleVersion := version.value,
    OsgiKeys.exportPackage := exportPkgs.map(e => mapPkg(name.value, e)),
    OsgiKeys.privatePackage := (Seq("internal") ++ privatePkgs).map(p => mapPkg(name.value, p)),
    OsgiKeys.importPackage := Seq(scalaRange.value) ++ importPkgs ++ Seq("*")
  )
}
