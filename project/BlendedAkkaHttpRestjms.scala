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
      Dependencies.sttp % "test",
      Dependencies.sttpAkka % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqClient % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.scalatest % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedCamelUtils.project,
      BlendedDomino.project,
      BlendedContainerContextApi.project,
      BlendedAkka.project,
      BlendedAkkaHttp.project,
      BlendedUtil.project,
      BlendedTestsupportPojosr.project % "test"
    )
  }
}
