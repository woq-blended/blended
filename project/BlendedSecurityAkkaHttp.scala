import sbt._
import blended.sbt.Dependencies

private object BlendedSecurityAkkaHttp extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.akka.http",
    description = "Some security aware Akka HTTP routes for the blended container",
    deps = Seq(
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
  )

  override val project = helper.baseProject.dependsOn(
    BlendedAkka.project,
    BlendedSecurityJvm.project,
    BlendedUtilLogging.project,
    BlendedTestsupport.project % "test"
  )
}

