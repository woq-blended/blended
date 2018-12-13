import sbt._

object BlendedAkkaHttpJmsqueue extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.akka.http.jmsqueue",
    description = "Provide a simple REST interface to consume messages from JMS Queues",
    deps = Seq(
      Dependencies.domino,
      Dependencies.jms11Spec,
      Dependencies.sttp % "test",
      Dependencies.sttpAkka % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaStreamTestkit % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test"
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedContainerContextApi.project,
    BlendedAkka.project,
    BlendedAkkaHttp.project,
    BlendedUtil.project,

    BlendedActivemqBrokerstarter.project % "test",
    BlendedStreams.project % "test",
    BlendedTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test"
  )
}
