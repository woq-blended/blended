import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbtcrossproject.CrossPlugin.autoImport._
import sbt._
import sbt.Keys._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._

object BlendedSecurityCross {

  val project = crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Full)
    .withoutSuffixFor(JVMPlatform)
    .in(file("blended.security"))

}

object BlendedSecurityJs extends ProjectHelper {

  override  val project  = BlendedSecurityCross.project.js.settings(
    Seq(
      name := "blendedSecurityJs",
       libraryDependencies ++= Seq(
        "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
        "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % "test"
      )
    )
  )
}

object BlendedSecurityJvm extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.security",
    "Configuration bundle for the security framework."
  ) {

    override  val projectFactory: () => Project = { () =>
      BlendedSecurityCross.project.jvm.settings(
        Seq(
          name := "blendedSecurityJvm"
        )
      )
    }

    override val libDeps = Seq(
      Dependencies.prickle,
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )

    override lazy val bundle: BlendedBundle = defaultBundle.copy(
      bundleActivator = "blended.security.internal.SecurityActivator",
      exportPackage = Seq(prjName, s"$prjName.json")
    )
  }
  override  val project  = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedDomino.project,
    BlendedUtil.project,
    BlendedSecurityBoot.project
  )
}
