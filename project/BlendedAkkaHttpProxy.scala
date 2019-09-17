import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._

object BlendedAkkaHttpProxy extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName : String = "blended.akka.http.proxy"
    override val description : String = "Provide Akka HTTP Proxy support"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.domino,
      Dependencies.akkaStream,
      Dependencies.akkaHttp,
      Dependencies.akkaActor,
      Dependencies.akkaSlf4j % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.scalatest % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.akkaStreamTestkit % Test,
      Dependencies.akkaHttpTestkit % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.springCore % Test,
      Dependencies.springBeans % Test,
      Dependencies.springContext % Test,
      Dependencies.springExpression % Test,
      Dependencies.commonsLogging % Test
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
      BlendedAkkaHttp.project,
      BlendedUtil.project,
      BlendedUtilLogging.project,
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
