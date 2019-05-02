import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedSamplesJms extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.samples.jms"
    override val description = "A combined JMS example"
    override val projectDir = Some("blended.samples/blended.samples.jms")

    override def deps = Seq(
      Dependencies.domino,
      Dependencies.camelCore,
      Dependencies.camelJms,
      Dependencies.geronimoJms11Spec,
      Dependencies.slf4j
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.JmsSampleActivator",
      exportPackage = Seq()
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedCamelUtils.project
    )
  }
}
