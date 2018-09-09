object BlendedJmx extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.jmx",
    "Helper bundle to expose the platform's MBeanServer as OSGI Service."
  ) {

    override val libDeps = Seq(
      Dependencies.domino
    )

    override lazy val bundle: BlendedBundle = defaultBundle.copy(
      bundleActivator = s"${prjName}.internal.BlendedJmxActivator"
    )
  }

  override  val project  = helper.baseProject
}
