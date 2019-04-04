import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedJmsSampler extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.jms.sampler"
    override val description = "A bundle to sample messages from a given JMS topic."

    override def deps = Seq(
      Dependencies.geronimoJms11Spec
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      exportPackage = Seq.empty,
      bundleActivator = s"${projectName}.internal.JmsSamplerActivator"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedAkka.project,
      BlendedUtil.project
    )
  }
}
