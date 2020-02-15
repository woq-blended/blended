import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedMgmtBase extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.mgmt.base"
    override val description : String = "Shared classes for management and reporting facility."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.scalatest % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.MgmtBaseActivator"
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedContainerContextApi.project,
      BlendedUtil.project,
      BlendedUtilLogging.project,

      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
