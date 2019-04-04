import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedAkkaHttp extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.akka.http"
    override val description = "Provide Akka HTTP support"

    override def deps = Seq(
      Dependencies.domino,
      Dependencies.orgOsgi,
      Dependencies.akkaStream,
      Dependencies.akkaOsgi,
      Dependencies.akkaHttp,
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.mockitoAll % "test",
      Dependencies.scalatest % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.akkaStreamTestkit % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.BlendedAkkaHttpActivator"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedContainerContextApi.project,
      BlendedDomino.project,
      BlendedUtil.project,
      BlendedUtilLogging.project,
      BlendedAkka.project,
      BlendedTestsupport.project % "test",
      BlendedTestsupportPojosr.project % "test"
    )
  }
}
