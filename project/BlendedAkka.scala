import sbt._
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import blended.sbt.Dependencies

object BlendedAkka extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.akka",
    description = "Provide OSGi services and API to use Actors in OSGi bundles with a shared ActorSystem.",
    deps = Seq(
      Dependencies.orgOsgi,
      Dependencies.akkaActor,
      Dependencies.domino,

      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.BlendedAkkaActivator",
      exportPackage = Seq(
        b.bundleSymbolicName,
        s"${b.bundleSymbolicName}.protocol"
      )
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "blended" -> "TRACE"
      )
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedContainerContextApi.project,
    BlendedDomino.project,

    BlendedTestsupport.project % "test"
  )
}
