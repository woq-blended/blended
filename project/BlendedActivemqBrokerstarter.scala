import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedActivemqBrokerstarter extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.activemq.brokerstarter"
    override val description : String =
      """
        |A simple wrapper around an Active MQ broker that makes sure that the broker is completely
        |started before exposing a connection factory OSGi service
      """.stripMargin

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.camelJms,
      Dependencies.activeMqBroker,
      Dependencies.activeMqSpring,

      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.activeMqKahadbStore
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.BrokerActivator"
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedJmsUtils.project,
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
