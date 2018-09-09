import sbt.Keys._
import com.typesafe.sbt.osgi.OsgiKeys

object BlendedJettyBoot
  extends ProjectSettings(
    prjName = "blended.jetty.boot",
    desc = "Bundle wrapping the original jetty boot bundle to dynamically provide SSL Context via OSGI services.",
    libDeps = Seq(
      Dependencies.domino,
      Dependencies.jettyOsgiBoot
    )
  ) {

  override def bundle: BlendedBundle = super.bundle.copy(
    bundleActivator = s"${prjName}.internal.JettyActivator"
  )

  override def settings: Seq[sbt.Setting[_]] = {
    super.settings ++ Seq(
      OsgiKeys.embeddedJars := {
        val jettyOsgi = BuildHelper.resolveModuleFile(Dependencies.jettyOsgiBoot.intransitive(), target.value)
        jettyOsgi.distinct
      }
    )
  }
}
