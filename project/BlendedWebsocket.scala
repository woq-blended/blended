import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedWebsocket extends ProjectFactory {
  //scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName = "blended.websocket"
    override val description = "The web socket server module."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.akkaHttp,
      Dependencies.akkaHttpCore,

      Dependencies.akkaTestkit % Test,
      Dependencies.sttp % Test,
      Dependencies.sttpAkka % Test,
      Dependencies.scalatest % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.jclOverSlf4j % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.WebSocketActivator",
      exportPackage = Seq(projectName)
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedAkkaHttp.project,
      BlendedSecurityLoginApi.project,

      BlendedTestsupport.project % Test,
      BlendedPersistence.project % Test,
      BlendedPersistenceH2.project % Test,
      BlendedSecurityLoginImpl.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedSecurityLoginRest.project % Test
    )
  }
}
