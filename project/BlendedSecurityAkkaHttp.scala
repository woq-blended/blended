import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

private object BlendedSecurityAkkaHttp extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.security.akka.http"
    override val description : String = "Some security aware Akka HTTP routes for the blended container"

    override def deps : Seq[ModuleID] = Seq(
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

