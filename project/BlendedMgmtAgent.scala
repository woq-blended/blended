import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedMgmtAgent extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.mgmt.agent"
    override val description : String = "Bundle to regularly report monitoring information to a central container hosting the container registry"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.orgOsgi,
      Dependencies.akkaOsgi,
      Dependencies.akkaHttp,
      Dependencies.akkaStream,
      Dependencies.akkaTestkit % Test,
      Dependencies.scalatest % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.AgentActivator",
      exportPackage = Seq()
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedUpdaterConfigJvm.project,
      BlendedUtilLogging.project,
      BlendedPrickleAkkaHttp.project
    )
  }
}
