import sbt._

object BlendedSecurityLoginRest extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.login.rest",
    description = "A REST service providing login services and web token management",
    deps = Seq(
      Dependencies.akkaHttp,
      Dependencies.akkaHttpCore,

      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.sttp % "test",
      Dependencies.sttpAkka % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.RestLoginActivator"
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedAkkaHttp.project,
    BlendedSecurityAkkaHttp.project,
    BlendedUtilLogging.project,
    BlendedSecurityLoginApi.project,

    BlendedTestsupportPojosr.project % "test",
    BlendedSecurityLoginImpl.project % "test"
  )
}
