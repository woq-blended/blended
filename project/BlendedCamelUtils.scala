import sbt._

object BlendedCamelUtils extends ProjectSettings(
  prjName = "blended.camel.utils",
  desc = "Useful helpers for Camel",
  libDeps = Seq(
    Dependencies.orgOsgi,
    Dependencies.orgOsgiCompendium,
    Dependencies.camelJms,
    Dependencies.slf4j
  )
)
