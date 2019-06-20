import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedSecurityLoginRest extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.security.login.rest"
    override val description : String = "A REST service providing login services and web token management"

    override def deps : Seq[ModuleID] = Seq(
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

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.RestLoginActivator"
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
