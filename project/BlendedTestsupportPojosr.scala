import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedTestsupportPojosr extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.testsupport.pojosr"
    override val description = "A simple Pojo based test container that can be used in unit testing"
    override val osgi = false

    override def deps = Seq(
      Dependencies.scalatest,
      Dependencies.felixConnect,
      Dependencies.orgOsgi
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedContainerContextImpl.project,
      BlendedDomino.project
    )
  }
}
