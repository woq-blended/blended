import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedAkkaHttp extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.akka.http"
    override val description : String = "Provide Akka HTTP support"

    override def deps : Seq[ModuleID] = Seq(
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

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.BlendedAkkaHttpActivator"
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
