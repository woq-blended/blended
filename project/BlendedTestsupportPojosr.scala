import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedTestsupportPojosr extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.testsupport.pojosr"
    override val description : String = "A simple Pojo based test container that can be used in unit testing"
    override val osgi : Boolean = false

    override def deps : Seq[ModuleID] = Seq(
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
