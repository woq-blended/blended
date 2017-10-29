import com.typesafe.sbt.osgi.OsgiKeys
import sbt._
import sbt.Keys._

object BuildHelper {

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
