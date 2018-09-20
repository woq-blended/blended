object BlendedJmx extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    "blended.jmx",
    "Helper bundle to expose the platform's MBeanServer as OSGI Service.",
    deps = Seq(
      Dependencies.domino
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.BlendedJmxActivator",
      exportPackage = Seq()
    )
  )

  override val project = helper.baseProject
}
