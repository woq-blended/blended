import sbt._

object BlendedAkka extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.akka",
    description = "Provide OSGi services and API to use Actors in OSGi bundles with a shared ActorSystem.",
    deps = Seq(
      Dependencies.orgOsgi,
      Dependencies.akkaActor,
      Dependencies.akkaOsgi,
      Dependencies.domino
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.BlendedAkkaActivator",
      exportPackage = Seq(
        b.bundleSymbolicName,
        s"${b.bundleSymbolicName}.protocol"
      )
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedContainerContextApi.project,
    BlendedDomino.project
  )
}
