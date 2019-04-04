import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedJmsUtils extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.jms.utils"
    override val description = "A bundle to provide a ConnectionFactory wrapper that monitors a single connection and is able to monitor the connection via an active ping."

    override def deps = Seq(
      Dependencies.camelJms,
      Dependencies.jms11Spec,
      Dependencies.scalatest % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.akkaStream % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedMgmtBase.project,
      BlendedContainerContextApi.project,
      BlendedUpdaterConfigJvm.project,
      BlendedUtilLogging.project,
      BlendedAkka.project,
      BlendedCamelUtils.project % "test",
      BlendedTestsupport.project % "test"
    )
  }
}
