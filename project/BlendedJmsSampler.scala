object BlendedJmsSampler
  extends ProjectSettings(
    prjName = "blended.jms.sampler",
    desc = "A bundle to sample messages from a given JMS topic."
  ) {

  override def libDependencies: scala.Seq[sbt.ModuleID] = Seq(
    Dependencies.geronimoJms11Spec
  )

  override def bundle: BlendedBundle = super.bundle.copy(
    exportPackage = Seq.empty,
    bundleActivator = s"$prjName.internal.JmsSamplerActivator"
  )
}
