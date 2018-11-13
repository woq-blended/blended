import sbt._

object BlendedActivemqBrokerstarter extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.activemq.brokerstarter",
    description = "A simple wrapper around an Active MQ broker that makes sure that the broker is completely started before exposing a connection factory OSGi service",
    deps = Seq(
      Dependencies.camelJms,
      Dependencies.activeMqBroker,
      Dependencies.activeMqSpring,

      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.activeMqKahadbStore
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.BrokerActivator"
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedAkka.project,
    BlendedJmsUtils.project,

    BlendedTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test"
  )
}
