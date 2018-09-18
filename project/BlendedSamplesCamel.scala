import sbt._

object BlendedSamplesCamel extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.samples.camel",
    description = "A sample camel route",
    deps = Seq(
      Dependencies.domino,
      Dependencies.camelCore,
      Dependencies.camelJms,
      Dependencies.geronimoJms11Spec,
      Dependencies.slf4j
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.CamelSampleActivator",
      exportPackage = Seq()
    ),
    projectDir = Some("blended.samples/blended.samples.camel")
  )

  override val project = helper.baseProject.dependsOn(
    BlendedCamelUtils.project
  )
}
