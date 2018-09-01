object BlendedContainerContextApi extends ProjectSettings(
  "blended.container.context.api",
  "The API for the Container Context and Identifier Services"
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    Dependencies.typesafeConfig,
    Dependencies.scalatest % "test",
    Dependencies.logbackCore % "test",
    Dependencies.logbackClassic % "test"
  )
}
