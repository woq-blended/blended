import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import blended.sbt.Dependencies
import sbt._

object BlendedWsJmx extends ProjectFactory {
  //scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName = "blended.ws.jmx"
    override val description = "A web socket protocol handler for JMX requests"

    override def deps : Seq[ModuleID]= Seq(
      Dependencies.akkaActor
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.WsJmxActivator",
      exportPackage = Seq.empty
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedJmxJvm.project,
      BlendedWebsocket.project,

      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedPersistenceH2.project % Test
    )
  }
}
