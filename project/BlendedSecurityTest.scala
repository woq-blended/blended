import sbt._

object BlendedSecurityTest extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.test",
    description = "Test cases for blended.security",
    osgi = false
  )
  override val project = helper.baseProject.dependsOn(
    BlendedTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test",
    BlendedSecurityLoginImpl.project % "test"
  )
}
