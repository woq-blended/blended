import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

private object BlendedSecurityLoginApi extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.security.login.api"
    override val description : String = "API to provide the backend for a Login Service"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.prickle,
      Dependencies.jjwt,
      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      importPackage = Seq("android.*;resolution:=optional")
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedAkka.project,
      BlendedSecurityJvm.project,
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
