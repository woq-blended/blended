import blended.sbt.Dependencies
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import phoenix.ProjectFactory
import sbt._

object BlendedJmsBridge extends ProjectFactory {

  object config extends ProjectSettings {
    override val projectName = "blended.jms.bridge"
    override val description = "A generic JMS bridge to connect the local JMS broker to en external JMS"

    override def deps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.typesafeConfig,

      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.activeMqBroker % Test,
      Dependencies.scalatest % Test,
      Dependencies.scalacheck % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.BridgeActivator"
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogLogPackages ++= Map(
        "" +
          "App" -> "DEBUG",
        "blended" -> "DEBUG",
        "spec" -> "DEBUG"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtil.project,
      BlendedUtilLogging.project,
      BlendedJmsUtils.project,
      BlendedDomino.project,
      BlendedAkka.project,
      BlendedStreams.project,

      BlendedActivemqBrokerstarter.project % Test,
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedStreamsTestsupport.project % Test
    )
  }
}
