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
      Dependencies.scalatest % Test,
      Dependencies.akkaStreamTestkit % Test,
      Dependencies.akkaHttpTestkit % Test,
      Dependencies.logbackClassic % Test
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project
    )
  }
}
