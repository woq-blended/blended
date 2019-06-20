import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import phoenix.ProjectFactory
import sbt._

object BlendedAkka extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name

    override val projectName = "blended.akka"
    override val description = "Provide OSGi services and API to use Actors in OSGi bundles with a shared ActorSystem."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.orgOsgi,
      Dependencies.akkaActor,
      Dependencies.domino,

      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.BlendedAkkaActivator",
      exportPackage = Seq(
        projectName,
        s"$projectName.protocol"
      )
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "blended" -> "TRACE"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedContainerContextApi.project,
      BlendedDomino.project,

      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
