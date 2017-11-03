import com.typesafe.sbt.osgi.SbtOsgi.autoImport._
import sbt._
import sbt.Keys._

object BuildHelper {

  def bundleSettings(
    exports: Seq[String] = Seq.empty,
    imports: Seq[String] = Seq.empty,
    privates: Seq[String] = Seq.empty
  ): Seq[Setting[_]] = {

    val scalaRange: String => String = { sv =>
      val v = sv.split("\\.").take(2).mkString(".")
      "scala.*;version=\"[" + v + "," + v + ".50)\""
    }

    def mapPkg(symbolicName : String, export: String) : String = { export match {
      case e if e.isEmpty => symbolicName
      case s if s.startsWith("/") => s.substring(1)
      case s => symbolicName + "." + s
    }}

    Seq(
      OsgiKeys.bundleSymbolicName := name.value,
      OsgiKeys.bundleVersion := version.value,
      OsgiKeys.exportPackage := exports.map(e => mapPkg(name.value, e)),
      OsgiKeys.privatePackage := (Seq("internal") ++ privates).map(p => mapPkg(name.value, p)),
      OsgiKeys.importPackage := Seq(scalaRange(scalaVersion.value)) ++ imports ++ Seq("*")
    )
  }
}
