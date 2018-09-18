import sbt._

private object BlendedSecurityLoginApi extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.login.api",
    description = "API to provide the backend for a Login Service",
    deps = Seq(
      Dependencies.prickle,
      Dependencies.jjwt,
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      importPackage = Seq("android.*;resolution:=optional")
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedAkka.project,
    BlendedSecurityJvm.project,
    BlendedTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test"
  )
}
