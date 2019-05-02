import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedMgmtRest extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.mgmt.rest"
    override val description = "REST interface to accept POST's from distributed containers. These will be delegated to the container registry."

    override def deps = Seq(
      Dependencies.akkaActor,
      Dependencies.domino,
      Dependencies.akkaHttp,
      Dependencies.akkaHttpCore,
      Dependencies.akkaStream,

      Dependencies.akkaStreamTestkit % Test,
      Dependencies.scalatest % Test,
      Dependencies.akkaHttpTestkit % Test,
      Dependencies.sttp % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.lambdaTest % Test,
      Dependencies.jclOverSlf4j % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.MgmtRestActivator",
      exportPackage = Seq()
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedAkkaHttp.project,
      BlendedUpdaterRemote.project,
      BlendedSecurityAkkaHttp.project,
      BlendedAkka.project,
      BlendedPrickleAkkaHttp.project,
      BlendedMgmtRepo.project,

      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedPersistenceH2.project % Test
    )
  }
}
