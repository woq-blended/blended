import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.OsgiKeys

object BlendedJettyBoot extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.jetty.boot",
     "Bundle wrapping the original jetty boot bundle to dynamically provide SSL Context via OSGI services."
  ) {

    override val libDeps = Seq(
      Dependencies.domino,
      Dependencies.jettyOsgiBoot
    )

    override lazy val bundle: BlendedBundle = defaultBundle.copy(
      bundleActivator = s"${prjName}.internal.JettyActivator"
    )

    override val settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      OsgiKeys.embeddedJars := {
        val jettyOsgi = BuildHelper.resolveModuleFile(Dependencies.jettyOsgiBoot.intransitive(), target.value)
        jettyOsgi.distinct
      }
    )
  }

  override  val project  = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedUtilLogging.project
  )
}
