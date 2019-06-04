import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedActivemqClient extends ProjectFactory {

  object config extends ProjectSettings {
    override val projectName = "blended.activemq.client"
    override val description = "An Active MQ Connection factory as a service"

    override def deps = Seq(
      Dependencies.activeMqClient,
      Dependencies.activeMqBroker % Test,
      Dependencies.activeMqKahadbStore % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.AmqClientActivator",
      exportPackage = Seq(projectName)
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedUtil.project,
      BlendedUtilLogging.project,
      BlendedJmsUtils.project,
      BlendedAkka.project,
      BlendedStreams.project,

      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
