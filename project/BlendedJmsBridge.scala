import sbt._

object BlendedJmsBridge extends ProjectFactory {

  private[this] val helper : ProjectSettings = new ProjectSettings(
    projectName = "blended.jms.bridge",
    description = "A generic JMS bridge to connect the local JMS broker to en external JMS",
    deps = Seq(
      Dependencies.typesafeConfig,

      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.scalatest % "test"
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedUtil.project,
    BlendedUtilLogging.project,
    BlendedJmsUtils.project,
    BlendedDomino.project,
    BlendedAkka.project,
    BlendedStreams.project,

    BlendedTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test"
  )
}
