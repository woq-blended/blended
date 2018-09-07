object BlendedAkka
  extends ProjectSettings(
    prjName = "blended.akka",
    desc = "Provide OSGi services and API to use Actors in OSGi bundles with a shared ActorSystem.",
    libDeps = Seq(
      Dependencies.orgOsgi,
      Dependencies.akkaActor,
      Dependencies.akkaOsgi,
      Dependencies.domino
    )
  ) {

  override def bundle: BlendedBundle = super.bundle.copy(
    exportPackage = Seq(prjName, s"$prjName.protocol")
  )

}

  
