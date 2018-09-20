object BlendedUtilLogging extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.util.logging",
    description = "Logging utility classes to use in other bundles",
    deps = Seq(
      Dependencies.slf4j
    )
  )

  override val project = helper.baseProject
}

