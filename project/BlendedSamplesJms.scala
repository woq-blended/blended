import sbt._
import blended.sbt.Dependencies

object BlendedSamplesJms extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.samples.jms",
    description = "A combined JMS example",
    projectDir = Some("blended.samples/blended.samples.jms"),
    deps = Seq(
      Dependencies.domino,
      Dependencies.camelCore,
      Dependencies.camelJms,
      Dependencies.geronimoJms11Spec,
      Dependencies.slf4j
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.JmsSampleActivator",
      exportPackage = Seq()
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedCamelUtils.project
  )
}
