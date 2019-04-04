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
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      importPackage = Seq("android.*;resolution:=optional")
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedAkka.project,
      BlendedSecurityJvm.project,
      BlendedTestsupport.project % "test",
      BlendedTestsupportPojosr.project % "test"
    )
  }
}
