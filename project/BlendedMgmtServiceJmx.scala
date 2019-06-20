import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedMgmtServiceJmx extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.mgmt.service.jmx"
    override val description : String = "A JMX based Service Info Collector."

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.ServiceJmxActivator",
      exportPackage = Seq()
    )

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.scalatest % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedUtilLogging.project,
      BlendedAkka.project,
      BlendedUpdaterConfigJvm.project
    )
  }
}
