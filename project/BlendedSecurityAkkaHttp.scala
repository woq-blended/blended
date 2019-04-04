import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

private object BlendedSecurityAkkaHttp extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.security.akka.http"
    override val description = "Some security aware Akka HTTP routes for the blended container"

    override def deps = Seq(
      Dependencies.akkaHttp,
      Dependencies.akkaStream,
      Dependencies.orgOsgi,
      Dependencies.orgOsgiCompendium,
      Dependencies.slf4j,
      Dependencies.commonsBeanUtils % "test",
      Dependencies.scalatest % "test",
      Dependencies.akkaStreamTestkit % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.jclOverSlf4j % "test",
      Dependencies.logbackClassic % "test"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedSecurityJvm.project,
      BlendedUtilLogging.project,
      BlendedTestsupport.project % "test"
    )
  }
}

