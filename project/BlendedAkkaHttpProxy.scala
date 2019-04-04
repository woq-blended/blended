import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedAkkaHttpProxy extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.akka.http.proxy"
    override val description = "Provide Akka HTTP Proxy support"

    override def deps = Seq(
      Dependencies.domino,
      Dependencies.akkaStream,
      Dependencies.akkaHttp,
      Dependencies.akkaActor,
      Dependencies.akkaSlf4j % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.scalatest % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaStreamTestkit % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedContainerContextApi.project,
      BlendedAkka.project,
      BlendedAkkaHttp.project,
      BlendedUtil.project,
      BlendedUtilLogging.project,
      BlendedTestsupport.project % "test",
      BlendedTestsupportPojosr.project % "test"
    )
  }
}
