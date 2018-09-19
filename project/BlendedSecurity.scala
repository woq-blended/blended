import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbtcrossproject.CrossPlugin.autoImport._
import sbt._
import sbt.Keys._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._

private object BlendedSecurityCross {

  private[this] val builder = sbtcrossproject
    .CrossProject("blendedSecurity", file("blended.security"))(JVMPlatform, JSPlatform)

  val project = builder
    .crossType(CrossType.Full)
    .build()
}

object BlendedSecurityJs extends ProjectFactory {

  override val project = {
    BlendedSecurityCross.project.js.settings(
      Seq(
        libraryDependencies ++= Seq(
          "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
          "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % "test"
        )
      )
    )
  }
}

object BlendedSecurityJvm extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security",
    description = "Configuration bundle for the security framework.",
    deps = Seq(
      Dependencies.prickle,
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.SecurityActivator",
      exportPackage = Seq(b.bundleSymbolicName, s"${b.bundleSymbolicName}.json")
    )
  ) {

    override def projectFactory: () => Project = { () =>
      BlendedSecurityCross.project.jvm.settings(
        Seq(
          name := "blendedSecurityJvm"
        )
      )
    }

  }

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedDomino.project,
    BlendedUtil.project,
    BlendedSecurityBoot.project
  )
}
