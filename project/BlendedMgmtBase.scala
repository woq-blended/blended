object BlendedMgmtBase
  extends ProjectSettings(
    prjName = "blended.mgmt.base",
    desc = "Shared classes for management and reporting facility.",
    libDeps = Seq(
      Dependencies.scalatest % "test"
    )
  ) {

  override def bundle: BlendedBundle = super.bundle.copy(
    bundleActivator = s"${prjName}.internal.MgmtBaseActivator"
  )

}
