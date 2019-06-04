import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedSamplesCamel extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.samples.camel"
    override val description = "A sample camel route"
    override val projectDir = Some("blended.samples/blended.samples.camel")

    override def deps = Seq(
      Dependencies.domino,
      Dependencies.camelCore,
      Dependencies.camelJms,
      Dependencies.geronimoJms11Spec,
      Dependencies.slf4j
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.CamelSampleActivator",
      exportPackage = Seq()
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedCamelUtils.project
    )
  }
}
