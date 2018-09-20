import sbt._

object BlendedAkkaHttpRestjms extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.akka.http.restjms",
    description = "Provide a simple REST interface to perform JMS request / reply operations",
    deps = Seq(
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
  )

  override val project = helper.baseProject.dependsOn(
    BlendedCamelUtils.project,
    BlendedDomino.project,
    BlendedContainerContextApi.project,
    BlendedAkka.project,
    BlendedAkkaHttp.project,
    BlendedUtil.project,
    BlendedTestsupportPojosr.project % "test"
  )
}
