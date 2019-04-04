import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedAkkaHttpJmsqueue extends ProjectFactory {
  object config extends ProjectSettings {

    override val projectName = "blended.akka.http.jmsqueue"
    override val description = "Provide a simple REST interface to consume messages from JMS Queues"

    override def deps = Seq(
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

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
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
}
