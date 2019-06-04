import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedStreamsTestsupport extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.streams.testsupport"
    override val description = "Some classes to make testing for streams a bit easier"
    override val osgi = false

    override def deps = Seq(
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
