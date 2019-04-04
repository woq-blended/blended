import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedSecurityLoginRest extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.security.login.rest"
    override val description = "A REST service providing login services and web token management"

    override def deps = Seq(
      Dependencies.akkaHttp,
      Dependencies.akkaHttpCore,

      Dependencies.scalatest % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaStreamTestkit % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.sttp % "test",
      Dependencies.sttpAkka % "test"
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.RestLoginActivator"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkkaHttp.project,
      BlendedSecurityAkkaHttp.project,
      BlendedUtilLogging.project,
      BlendedSecurityLoginApi.project,

      BlendedTestsupport.project % "test",
      BlendedTestsupportPojosr.project % "test",
      BlendedSecurityLoginImpl.project % "test"
    )
  }
}
