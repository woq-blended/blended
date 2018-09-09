object BlendedJmx extends
  ProjectSettings(
    prjName = "blended.jmx",
    desc = "Helper bundle to expose the platform's MBeanServer as OSGI Service.",
    libDeps = Seq(
      Dependencies.domino
    )
  ) {

  override def bundle: BlendedBundle = super.bundle.copy(
    bundleActivator = s"${prjName}.internal.BlendedJmxActivator"
  )

}

