import sbt._

object BlendedJmsSampler extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.jms.sampler",
    "A bundle to sample messages from a given JMS topic."
  ) {

    override def libDeps = Seq(
      Dependencies.geronimoJms11Spec
    )

    override def bundle: BlendedBundle = defaultBundle.copy(
      exportPackage = Seq.empty,
      bundleActivator = s"$prjName.internal.JmsSamplerActivator"
    )
  }

  override  val project  = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedAkka.project,
    BlendedUtil.project
  )
}
