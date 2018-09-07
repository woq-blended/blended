object BlendedUtilLogging
  extends ProjectSettings(
    prjName = "blended.util.logging",
    desc = "Logging utility classes to use in other bundles.",
    libDeps = Seq(
      Dependencies.slf4j
    )
  )
