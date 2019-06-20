import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedPrickleAkkaHttp extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.prickle.akka.http"
    override val description : String = "Define some convenience to use Prickle with Akka HTTP"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.akkaHttpCore,
      Dependencies.akkaHttp,
      Dependencies.akkaStream,
      Dependencies.prickle,
      Dependencies.scalatest % Test,
      Dependencies.akkaStreamTestkit % Test,
      Dependencies.akkaHttpTestkit % Test,
      Dependencies.logbackClassic % Test
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project
    )
  }
}
