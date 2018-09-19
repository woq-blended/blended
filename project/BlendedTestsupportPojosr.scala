import sbt._

object BlendedTestsupportPojosr extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    "blended.testsupport.pojosr",
    "A simple Pojo based test container that can be used in unit testing",
    osgi = false,
    deps = Seq(
      Dependencies.felixConnect
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedContainerContextImpl.project,
    BlendedDomino.project
  )
}
