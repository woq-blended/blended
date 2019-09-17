import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._

object BlendedAkkaHttpRestjms extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName : String = "blended.akka.http.restjms"
    override val description : String = "Provide a simple REST interface to perform JMS request / reply operations"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.domino,
      Dependencies.akkaStream,
      Dependencies.akkaHttp,
      Dependencies.akkaActor,
      Dependencies.jms11Spec,
      Dependencies.sttp % Test,
      Dependencies.sttpAkka % Test,
      Dependencies.activeMqBroker % Test,
      Dependencies.activeMqClient % Test,
      Dependencies.springBeans % Test,
      Dependencies.springContext % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "DEBUG",
        "spec" -> "DEBUG",
        "blended" -> "DEBUG"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedContainerContextApi.project,
      BlendedAkka.project,
      BlendedStreams.project,
      BlendedAkkaHttp.project,
      BlendedUtil.project,
      BlendedTestsupport.project % Test,
      BlendedActivemqBrokerstarter.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
