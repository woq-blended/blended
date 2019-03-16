import blended.sbt.Dependencies

object BlendedContainerContextApi extends ProjectFactory {

  private[this] val helper: ProjectSettings = new ProjectSettings(
    projectName = "blended.container.context.api",
    description = "The API for the Container Context and Identifier Services",
    deps = Seq(
      Dependencies.typesafeConfig
    ),
    adaptBundle = b => b.copy(
      importPackage = Seq(
        "blended.launcher.runtime;resolution:=optional"
      )
    )
  )

  override val project = helper.baseProject

}
