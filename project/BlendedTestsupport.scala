import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedTestsupport extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.testsupport"
    override val description = "Some test helper classes."
    override val osgi = false

    override def deps = super.deps ++ Seq(
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
