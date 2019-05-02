import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedJmx extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.jmx"
    override val description = "Helper bundle to expose the platform's MBeanServer as OSGI Service."

    override def deps = Seq(
      Dependencies.domino
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.BlendedJmxActivator",
      exportPackage = Seq()
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
