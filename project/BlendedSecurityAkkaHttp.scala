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
      Dependencies.commonsBeanUtils % Test,
      Dependencies.scalatest % Test,
      Dependencies.akkaStreamTestkit % Test,
      Dependencies.akkaHttpTestkit % Test,
      Dependencies.jclOverSlf4j % Test,
      Dependencies.logbackClassic % Test
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedSecurityJvm.project,
      BlendedUtilLogging.project,
      BlendedTestsupport.project % Test
    )
  }
}

