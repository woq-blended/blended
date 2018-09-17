import sbt._

object BlendedMgmtRepoRest extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.mgmt.repo.rest",
    description = "File Artifact Repository REST Service",
    deps = Seq(
      Dependencies.akkaHttp,
      Dependencies.sprayJson,
      Dependencies.scalatest % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.ArtifactRepoRestActivator",
      exportPackage = Seq()
    )
  )

  override val project = helper.baseProject.dependsOn(
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
