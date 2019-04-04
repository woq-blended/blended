import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedPrickleAkkaHttp extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.prickle.akka.http"
    override val description = "Define some convenience to use Prickle with Akka HTTP"

    override def deps = Seq(
      Dependencies.akkaHttpCore,
      Dependencies.akkaHttp,
      Dependencies.akkaStream,
      Dependencies.prickle,
      Dependencies.scalatest % "test",
      Dependencies.akkaStreamTestkit % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.logbackClassic % "test"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project
    )
  }
}
