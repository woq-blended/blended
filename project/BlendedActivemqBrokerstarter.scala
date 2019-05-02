import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedActivemqBrokerstarter extends ProjectFactory {
  
  object config extends ProjectSettings {
    override val projectName = "blended.activemq.brokerstarter"
    override val description = "A simple wrapper around an Active MQ broker that makes sure that the broker is completely started before exposing a connection factory OSGi service"

    override def deps = Seq(
      Dependencies.camelJms,
      Dependencies.activeMqBroker,
      Dependencies.activeMqSpring,

      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.activeMqKahadbStore
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.BrokerActivator"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedJmsUtils.project,
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
