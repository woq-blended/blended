import sbt._
import blended.sbt.Dependencies

object BlendedTestsupportPojosr extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    "blended.testsupport.pojosr",
    "A simple Pojo based test container that can be used in unit testing",
    osgi = false,
    deps = Seq(
      Dependencies.scalatest,
      Dependencies.felixConnect,
      Dependencies.orgOsgi
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedContainerContextImpl.project,
    BlendedDomino.project
  )
}
