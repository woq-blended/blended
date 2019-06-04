import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedSecurityLoginRest extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.security.login.rest"
    override val description = "A REST service providing login services and web token management"

    override def deps = Seq(
      Dependencies.akkaHttp,
      Dependencies.akkaHttpCore,

      Dependencies.scalatest % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.akkaStreamTestkit % Test,
      Dependencies.akkaHttpTestkit % Test,
      Dependencies.sttp % Test,
      Dependencies.sttpAkka % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.RestLoginActivator"
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkkaHttp.project,
      BlendedSecurityAkkaHttp.project,
      BlendedUtilLogging.project,
      BlendedSecurityLoginApi.project,

      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedSecurityLoginImpl.project % Test
    )
  }
}
