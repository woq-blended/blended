import sbt._
import blended.sbt.Dependencies

object BlendedJmsSampler extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.jms.sampler",
    description = "A bundle to sample messages from a given JMS topic.",
    deps = Seq(
      Dependencies.geronimoJms11Spec
    ),
    adaptBundle = b => b.copy(
      exportPackage = Seq.empty,
      bundleActivator = s"${b.bundleSymbolicName}.internal.JmsSamplerActivator"
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedAkka.project,
    BlendedUtil.project
  )
}
