import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedJmsBridge extends ProjectFactory {

  object config extends ProjectSettings {
    override val projectName = "blended.jms.bridge"
    override val description = "A generic JMS bridge to connect the local JMS broker to en external JMS"

    override def deps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.typesafeConfig,

      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.scalatest % "test",
      Dependencies.scalacheck % "test"
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.BridgeActivator"
    )

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogLogPackages ++= Map(
        "" +
        "App" -> "DEBUG",
        "blended" -> "TRACE"
      )
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtil.project,
      BlendedUtilLogging.project,
      BlendedJmsUtils.project,
      BlendedDomino.project,
      BlendedAkka.project,
      BlendedStreams.project,

      BlendedActivemqBrokerstarter.project % "test",
      BlendedTestsupport.project % "test",
      BlendedTestsupportPojosr.project % "test",
      BlendedStreamsTestsupport.project % "test"
    )
  }
}
