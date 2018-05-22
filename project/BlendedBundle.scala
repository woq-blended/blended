import com.typesafe.sbt.osgi.SbtOsgi.autoImport._
import org.osgi.framework.BundleActivator
import sbt.Keys._
import sbt._

object BlendedBundle {


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
    * @return
    */
  def apply(
    bundleSymbolicName: String = null,
    bundleVersion: String = null,
    bundleActivator: String = null,
    importPackage: Seq[String] = null,
    privatePackage: Seq[String] = null,
    exportPackage: Seq[String] = null,
    embeddedJars: Setting[Task[Seq[sbt.File]]] = null,
    exportContents: Seq[String] = null,
    additionalHeaders: Map[String, String] = null
  ): Seq[Setting[_]] = {

    val extraEntries: Map[String, String] =
      Option(additionalHeaders).getOrElse(Map()) ++
        (Option(exportContents).map(c => Map("-exportcontents" -> c.mkString(","))).getOrElse(Map()))


    osgiSettings ++
      Seq(
        OsgiKeys.bundleSymbolicName := Option(bundleSymbolicName).getOrElse(name.value),
        OsgiKeys.bundleVersion := Option(bundleVersion).getOrElse(version.value),
        OsgiKeys.bundleActivator := Option(bundleActivator),
        OsgiKeys.importPackage :=
          Seq(
            BuildHelper.scalaRangeImport(scalaBinaryVersion.value),
          ) ++ Option(importPackage).getOrElse(Seq("*")
          ),
        // ensure we build a package with OSGi Manifest
        packageBin.in(Compile) := {
          packageBin.in(Compile).value
          OsgiKeys.bundle.value
        }
      ) ++
      Option(exportPackage).map(e => OsgiKeys.exportPackage := e) ++
      Option(privatePackage).map(p => OsgiKeys.privatePackage := p) ++
      Option(embeddedJars) ++
      (if (extraEntries.isEmpty) Seq() else {
        OsgiKeys.additionalHeaders := extraEntries
      })
  }
}