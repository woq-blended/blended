import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedAkkaHttpJmsqueue extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name

    override val projectName : String = "blended.akka.http.jmsqueue"
    override val description : String = "Provide a simple REST interface to consume messages from JMS Queues"

    override def deps : Seq[ModuleID] = Seq(
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

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
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
