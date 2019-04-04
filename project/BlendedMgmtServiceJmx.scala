import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedMgmtServiceJmx extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.mgmt.service.jmx"
    override val description = "A JMX based Service Info Collector."

    override def bundle: BlendedBundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.ServiceJmxActivator",
      exportPackage = Seq()
    )

    override def deps = Seq(
      Dependencies.scalatest % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedUtilLogging.project,
      BlendedAkka.project,
      BlendedUpdaterConfigJvm.project
    )
  }
}
