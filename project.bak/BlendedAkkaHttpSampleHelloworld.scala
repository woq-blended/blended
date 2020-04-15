import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedAkkaHttpSampleHelloworld extends ProjectFactory {
  //scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName = "blended.akka.http.sample.helloworld"
    override val description = "A sample Akka HTTP bases HTTP endpoint for the blended container"
    override val projectDir = Some("blended.samples/blended.akka.http.sample.helloworld")

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.domino,
      Dependencies.orgOsgi,
      Dependencies.orgOsgiCompendium,
      Dependencies.slf4j,
      Dependencies.scalatest % Test,
      Dependencies.slf4jLog4j12 % Test,
      Dependencies.akkaStreamTestkit % Test,
      Dependencies.akkaHttpTestkit % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.HelloworldActivator",
      exportPackage = Seq()
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedAkkaHttp.project,
      BlendedAkkaHttpApi.project
    )
  }
}
