import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedMgmtRepoRest extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.mgmt.repo.rest"
    override val description = "File Artifact Repository REST Service"

    override def deps = Seq(
      Dependencies.akkaHttp,
      Dependencies.scalatest % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.ArtifactRepoRestActivator",
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
