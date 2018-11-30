import sbt._

object BlendedAkkaHttpSampleHelloworld extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.akka.http.sample.helloworld",
    description = "A sample Akka HTTP bases HTTP endpoint for the blended container",
    projectDir = Some("blended.samples/blended.akka.http.sample.helloworld"),
    deps = Seq(
      Dependencies.domino,
      Dependencies.orgOsgi,
      Dependencies.orgOsgiCompendium,
      Dependencies.slf4j,
      Dependencies.scalatest % "test",
      Dependencies.slf4jLog4j12 % "test",
      Dependencies.akkaStreamTestkit % "test",
      Dependencies.akkaHttpTestkit % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.HelloworldActivator",
      exportPackage = Seq()
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedAkka.project,
    BlendedAkkaHttp.project,
    BlendedAkkaHttpApi.project
  )
}
