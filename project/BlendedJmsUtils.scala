import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedJmsUtils extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name

    override val projectName : String = "blended.jms.utils"
    override val description : String =
      "A bundle to provide a ConnectionFactory wrapper that monitors a single connection and is able to monitor the connection via an active ping."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.camelJms,
      Dependencies.jms11Spec,
      Dependencies.scalatest % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.akkaStream % Test,
      Dependencies.activeMqBroker % Test,
      Dependencies.activeMqKahadbStore % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedMgmtBase.project,
      BlendedContainerContextApi.project,
      BlendedUpdaterConfigJvm.project,
      BlendedUtilLogging.project,
      BlendedAkka.project,
      BlendedCamelUtils.project % Test,
      BlendedTestsupport.project % Test
    )
  }
}
