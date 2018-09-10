object BlendedUtil extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.util",
     "Utility classes to use in other bundles."
  ) {

    override def libDeps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaSlf4j,
      Dependencies.slf4j,
      Dependencies.akkaTestkit % "test",
      Dependencies.scalatest % "test",
      Dependencies.junit % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test"
    )

    override def bundle: BlendedBundle = defaultBundle.copy(
      exportPackage = Seq(prjName, s"$prjName.protocol", s"$prjName.config")
    )
  }

  override  val project  = helper.baseProject
}

