import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedAkkaHttpRestjms extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.akka.http.restjms"
    override val description = "Provide a simple REST interface to perform JMS request / reply operations"

    override def deps = Seq(
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

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
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
