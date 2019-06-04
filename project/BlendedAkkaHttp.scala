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
      Dependencies.akkaTestkit % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.mockitoAll % Test,
      Dependencies.scalatest % Test,
      Dependencies.akkaHttpTestkit % Test,
      Dependencies.akkaStreamTestkit % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.BlendedAkkaHttpActivator"
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedContainerContextApi.project,
      BlendedDomino.project,
      BlendedUtil.project,
      BlendedUtilLogging.project,
      BlendedAkka.project,
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
