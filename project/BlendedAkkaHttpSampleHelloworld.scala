import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedAkkaHttpSampleHelloworld extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.akka.http.sample.helloworld"
    override val description = "A sample Akka HTTP bases HTTP endpoint for the blended container"
    override val projectDir = Some("blended.samples/blended.akka.http.sample.helloworld")

    override def deps = Seq(
      Dependencies.domino,
      Dependencies.orgOsgi,
      Dependencies.orgOsgiCompendium,
      Dependencies.slf4j,
      Dependencies.scalatest % Test,
      Dependencies.slf4jLog4j12 % Test,
      Dependencies.akkaStreamTestkit % Test,
      Dependencies.akkaHttpTestkit % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.HelloworldActivator",
      exportPackage = Seq()
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedAkkaHttp.project,
      BlendedAkkaHttpApi.project
    )
  }
}
