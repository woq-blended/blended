import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedMgmtRepo extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.mgmt.repo"
    override val description = "File Artifact Repository"

    override def deps = Seq(
      Dependencies.scalatest % "test",
      Dependencies.lambdaTest % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test",
      Dependencies.scalatest % "test"
    )

    override def bundle: BlendedBundle = super.bundle.copy(
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
      BlendedTestsupport.project % "test"
    )
  }
}
