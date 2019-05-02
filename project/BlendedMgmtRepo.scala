import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedMgmtRepo extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.mgmt.repo"
    override val description = "File Artifact Repository"

    override def deps = Seq(
      Dependencies.scalatest % Test,
      Dependencies.lambdaTest % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.scalatest % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.ArtifactRepoActivator",
      privatePackage = Seq(
        s"${projectName}.file.*",
        s"${projectName}.internal.*"
      )
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedUpdaterConfigJvm.project,
      BlendedUtilLogging.project,
      BlendedMgmtBase.project,
      BlendedTestsupport.project % Test
    )
  }
}
