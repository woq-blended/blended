import phoenix.ProjectFactory
import sbt._

object BlendedSecurityTest extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.security.test"
    override val description : String = "Test cases for blended.security"
    override val osgi : Boolean = false

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedSecurityLoginImpl.project % Test
    )
  }
}
