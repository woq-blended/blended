import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedAkkaHttpRestjms extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName : String = "blended.akka.http.restjms"
    override val description : String = "Provide a simple REST interface to perform JMS request / reply operations"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.domino,
      Dependencies.akkaStream,
      Dependencies.akkaHttp,
      Dependencies.akkaActor,
      Dependencies.camelCore,
      Dependencies.camelJms,
      Dependencies.jms11Spec,
      Dependencies.sttp % Test,
      Dependencies.sttpAkka % Test,
      Dependencies.activeMqBroker % Test,
      Dependencies.activeMqClient % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.scalatest % Test,
      Dependencies.akkaHttpTestkit % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedCamelUtils.project,
      BlendedDomino.project,
      BlendedContainerContextApi.project,
      BlendedAkka.project,
      BlendedAkkaHttp.project,
      BlendedUtil.project,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
