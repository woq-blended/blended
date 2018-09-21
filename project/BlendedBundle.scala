import java.nio.file.Files

import com.typesafe.sbt.osgi.SbtOsgi.autoImport._
import sbt.Keys._
import sbt._

object BlendedBundle {
  //  def blendedRangeImport: String = """blended.*;version="[2.5,3)""""

  def scalaRangeImport(scalaBinaryVersion: String): String = s"""scala.*;version="[$scalaBinaryVersion,$scalaBinaryVersion.50)""""
}

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
 *                       OsgiKeys.embeddedJars := dependencyClasspath.in(Compile).value.files
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
  importPackage: Seq[String] = Seq.empty,
  privatePackage: Seq[String] = Seq.empty,
  exportPackage: Seq[String] = Seq.empty,
  embeddedJars: Setting[Task[Seq[sbt.File]]] = null,
  exportContents: Seq[String] = Seq.empty,
  additionalHeaders: Map[String, String] = Map.empty,
  defaultImports: Boolean = true
) {

  private[this] lazy val extraEntries: Seq[Setting[_]] = Seq(
    OsgiKeys.additionalHeaders ++= Map(
      "Bundle-Name" -> bundleSymbolicName,
      "Bundle-Description" -> description.value
    ) ++
      Option(exportContents).map(c => Map("-exportcontents" -> c.mkString(","))).getOrElse(Map()) ++
      additionalHeaders
  )

  val osgiSettings: Seq[Setting[_]] = Seq(
    // This setting does not seem to have an effect as it does not fail if a package is missing from the jar
    // OsgiKeys.failOnUndecidedPackage := true,

    OsgiKeys.bundleSymbolicName := Option(bundleSymbolicName).getOrElse(name.value),
    OsgiKeys.bundleVersion := Option(bundleVersion).getOrElse(version.value),
    OsgiKeys.bundleActivator := Option(bundleActivator),
    OsgiKeys.importPackage := {
      val effectiveImports = (if (defaultImports) {
        Seq(BlendedBundle.scalaRangeImport(scalaBinaryVersion.value))
      } else {
        Seq.empty
      }) ++
        importPackage ++
        (if (defaultImports) {
          Seq("*")
        } else {
          Seq.empty
        })

      sLog.value.debug(s"Effective package imports for [$bundleSymbolicName] : [$effectiveImports]")

      effectiveImports
    },
    OsgiKeys.exportPackage := exportPackage,
    OsgiKeys.privatePackage := privatePackage,
    // ensure we build a package with OSGi Manifest
    Compile / packageBin := {
      OsgiKeys.bundle
    }.dependsOn(Def.task[Unit] {
      // Make sure the classes directory exists before we start bundling
      // to avoid unnecessary bnd errors
      val log = streams.value.log
      val classDir = (Compile / classDirectory).value
      if (!classDir.exists()) {
        Files.createDirectories(classDir.toPath())
      }
    }).value
  ) ++
    Option(embeddedJars) ++
    extraEntries
}
