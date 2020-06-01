import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._

object BlendedActivemqClient extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.activemq.client"
    override val description : String = "An Active MQ Connection factory as a service"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.activeMqClient,
      Dependencies.activeMqBroker % Test,
      Dependencies.activeMqKahadbStore % Test,
      Dependencies.springCore % Test,
      Dependencies.springBeans % Test,
      Dependencies.springContext % Test,
      Dependencies.springExpression % Test,
      Dependencies.commonsLogging % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.AmqClientActivator",
      exportPackage = Seq(projectName)
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedUtil.project,
      BlendedUtilLogging.project,
      BlendedJmsUtils.project,
      BlendedAkka.project,
      BlendedStreams.project,

      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
