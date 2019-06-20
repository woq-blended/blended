import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedTestsupport extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.testsupport"
    override val description : String = "Some test helper classes."
    override val osgi = false

    override def deps : Seq[ModuleID] = super.deps ++ Seq(
      Dependencies.commonsIo,
      Dependencies.akkaActor,
      Dependencies.akkaTestkit,
      Dependencies.akkaCamel,
      Dependencies.camelCore,
      Dependencies.camelJms,
      Dependencies.scalatest,
      Dependencies.junit
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtil.project,
      BlendedUtilLogging.project,
      BlendedSecurityBoot.project
    )
  }
}
