import sbt._

object BlendedAkka extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.akka",
    "Provide OSGi services and API to use Actors in OSGi bundles with a shared ActorSystem."
  ) {

    override val libDeps = Seq(
      Dependencies.orgOsgi,
      Dependencies.akkaActor,
      Dependencies.akkaOsgi,
      Dependencies.domino
    )

    override lazy val bundle: BlendedBundle = defaultBundle.copy(
      exportPackage = Seq(prjName, s"$prjName.protocol")
    )
  }
  override  val project  = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedContainerContextApi.project,
    BlendedDomino.project
  )
}
