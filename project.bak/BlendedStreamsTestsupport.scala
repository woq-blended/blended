import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedStreamsTestsupport extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.streams.testsupport"
    override val description : String = "Some classes to make testing for streams a bit easier"
    override val osgi : Boolean = false

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.scalacheck,
      Dependencies.scalatest,
      Dependencies.akkaTestkit,
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.akkaPersistence,
      Dependencies.logbackCore,
      Dependencies.logbackClassic
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedStreams.project,
      BlendedTestsupport.project
    )
  }
}
