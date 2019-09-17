import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._

object BlendedActivemqBrokerstarter extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.activemq.brokerstarter"
    override val description : String =
      """
        |A simple wrapper around an Active MQ broker that makes sure that the broker is completely
        |started before exposing a connection factory OSGi service
      """.stripMargin

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.activeMqBroker,
      Dependencies.activeMqSpring,

      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.activeMqKahadbStore,
      Dependencies.springCore % Test,
      Dependencies.springBeans % Test,
      Dependencies.springContext % Test,
      Dependencies.springExpression % Test,
      Dependencies.commonsLogging % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.BrokerActivator"
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "Debug",
        "spec" -> "Debug",
        "blended" -> "Debug"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedJmsUtils.project,
      BlendedSecurityBoot.project,
      BlendedSecurityJvm.project,
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
