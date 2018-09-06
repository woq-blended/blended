object BlendedMgmtBase extends ProjectSettings(
  "blended.mgmt.base",
  "Shared classes for management and reporting facility."
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    Dependencies.scalatest % "test"
  )

  override def bundle: BlendedBundle = super.bundle.copy(
    bundleActivator = s"${prjName}.internal.MgmtBaseActivator"
  )

}
