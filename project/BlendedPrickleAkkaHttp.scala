import sbt._

object BlendedPrickleAkkaHttp extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.prickle.akka.http",
    description = "Define some convenience to use Prickle with Akka HTTP",
    deps = Seq(
      Dependencies.akkaHttpCore,
      Dependencies.akkaHttp,
      Dependencies.akkaStream,
      Dependencies.prickle,
      Dependencies.scalatest % "test",
      Dependencies.akkaStreamTestkit % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.logbackClassic % "test"
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project
  )
}
