import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedMgmtRepoRest extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.mgmt.repo.rest"
    override val description : String = "File Artifact Repository REST Service"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.akkaHttp,
      Dependencies.scalatest % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.ArtifactRepoRestActivator",
      exportPackage = Seq()
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedUpdaterConfigJvm.project,
      BlendedMgmtBase.project,
      BlendedMgmtRepo.project,
      BlendedSecurityAkkaHttp.project,
      BlendedUtil.project,
      BlendedUtilLogging.project,
      BlendedAkkaHttp.project
    )
  }
}
