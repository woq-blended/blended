import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

private object BlendedSecurityLoginApi extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.security.login.api"
    override val description = "API to provide the backend for a Login Service"

    override def deps = Seq(
      Dependencies.prickle,
      Dependencies.jjwt,
      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      importPackage = Seq("android.*;resolution:=optional")
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedAkka.project,
      BlendedSecurityJvm.project,
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
