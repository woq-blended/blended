import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedMgmtRepo extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.mgmt.repo"
    override val description : String = "File Artifact Repository"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.scalatest % Test,
      Dependencies.lambdaTest % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.scalatest % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.ArtifactRepoActivator",
      privatePackage = Seq(
        s"$projectName.file.*",
        s"$projectName.internal.*"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedUpdaterConfigJvm.project,
      BlendedUtilLogging.project,
      BlendedMgmtBase.project,
      BlendedTestsupport.project % Test
    )
  }
}
