import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedSamplesJms extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName = "blended.samples.jms"
    override val description = "A combined JMS example"
    override val projectDir = Some("blended.samples/blended.samples.jms")

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.domino,
      Dependencies.camelCore,
      Dependencies.camelJms,
      Dependencies.geronimoJms11Spec,
      Dependencies.slf4j
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.JmsSampleActivator",
      exportPackage = Seq()
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedCamelUtils.project
    )
  }
}
