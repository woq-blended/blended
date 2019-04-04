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
      Dependencies.sttp % Test,
      Dependencies.sttpAkka % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.akkaStreamTestkit % Test,
      Dependencies.akkaHttpTestkit % Test,
      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.activeMqBroker % Test,
      Dependencies.activeMqKahadbStore % Test
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedContainerContextApi.project,
      BlendedAkka.project,
      BlendedAkkaHttp.project,
      BlendedUtil.project,

      BlendedActivemqBrokerstarter.project % Test,
      BlendedStreams.project % Test,
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )

  }
}
