import Dependencies._

object BlendedAkka extends ProjectSettings(
  prjName = "blended.akka",
  desc = "Provide OSGi services and API to use Actors in OSGi bundles with a shared ActorSystem."
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    orgOsgi,
    akkaActor,
    akkaOsgi,
    domino
  )

  override def bundle: BlendedBundle = super.bundle.copy(
    exportPackage = Seq(prjName, s"$prjName.protocol")
  )

}
