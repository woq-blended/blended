import phoenix.ProjectFactory
import sbt._

object BlendedSecurityTest extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.security.test"
    override val description = "Test cases for blended.security"
    override val osgi = false

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedSecurityLoginImpl.project % Test
    )
  }
}
