import com.typesafe.sbt.osgi.SbtOsgi.autoImport._
import sbt.Keys._
import sbt._

/**
  * Create a bundle with proper Manifest headers.
  *
  * @param bundleSymbolicName
  * The `Bundle-SymbolicName`. Defaults to sbt setting `name`.
  * @param bundleVersion
  * The `Bundle-Version`. Defaults to sbt setting `version`.
  * @param bundleActivator
  * The `Bundle-Activator`, if any.
  * @param importPackage
  * The `Import-Package`. Defaults to `*`.
  * Also, all `scala.*` imports are properly restricted to a version range relative to the sbt setting `scalaBinaryVersion`.
  * @param privatePackage
  * The `Private-Package`.
  * @param embeddedJars
  * A set of jars to be embedded into the bundle as JARs. Those will also be added to the `Bundle-Classpath`.
  * Example:
  * {{{
  *                   OsgiKeys.embeddedJars := dependencyClasspath.in(Compile).value.files
  * }}}
  * The value is a rather complex TaskKey to support references to other tasks and settings via `.value`.
  * @param exportContents
  * The `-exportcontents` directive of bnd tool.
  * @param additionalHeaders
  * A map with additional manifest entries.
  */
case class BlendedBundle(
    bundleSymbolicName: String = null,
    bundleVersion: String = null,
    bundleActivator: String = null,
    importPackage: Seq[String] = null,
    privatePackage: Seq[String] = Seq.empty,
    exportPackage: Seq[String] = Seq.empty,
    embeddedJars: Setting[Task[Seq[sbt.File]]] = null,
    exportContents: Seq[String] = null,
    additionalHeaders: Map[String, String] = Map.empty
  ) {

  private[this] lazy val extraEntries : Seq[Setting[_]] = Seq(
    OsgiKeys.additionalHeaders ++= Map(
      "Bundle-Name" -> bundleSymbolicName,
      "Bundle-Description" -> description.value
    ) ++
    Option(exportContents).map(c => Map("-exportcontents" -> c.mkString(","))).getOrElse(Map()) ++
    additionalHeaders
  )


  lazy val osgiSettings : Seq[Setting[_]] = Seq(
    OsgiKeys.bundleSymbolicName := Option(bundleSymbolicName).getOrElse(name.value),
    OsgiKeys.bundleVersion := Option(bundleVersion).getOrElse(version.value),
    OsgiKeys.bundleActivator := Option(bundleActivator),
    OsgiKeys.importPackage :=
      Seq(
        BuildHelper.scalaRangeImport(scalaBinaryVersion.value),
      ) ++ Option(importPackage).getOrElse(Seq("*")
      ),
    OsgiKeys.exportPackage := exportPackage,
    OsgiKeys.privatePackage := privatePackage,
    // ensure we build a package with OSGi Manifest
    Compile/packageBin := {
      packageBin.in(Compile).value
      OsgiKeys.bundle.value
    }
  ) ++
  Option(embeddedJars) ++
  extraEntries
}
