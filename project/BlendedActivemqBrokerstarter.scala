import sbt._

object BlendedActivemqBrokerstarter extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.activemq.brokerstarter",
    "A simple wrapper around an Active MQ broker that makes sure that the broker is completely started before exposing a connection factory OSGi service"
  ) {

    override def libDeps = Seq(
      Dependencies.camelJms,
      Dependencies.activeMqBroker,
      Dependencies.activeMqSpring
    )

  }

  override  val project  = helper.baseProject.dependsOn(
    BlendedAkka.project,
    BlendedJmsUtils.project
  )
}
