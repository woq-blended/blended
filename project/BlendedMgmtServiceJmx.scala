import sbt._

object BlendedMgmtServiceJmx extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.mgmt.service.jmx",
    description = "A JMX based Service Info Collector.",
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.ServiceJmxActivator"
    ),
    deps = Seq(
      Dependencies.scalatest % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedUtilLogging.project,
    BlendedAkka.project,
    BlendedUpdaterConfigJvm.project
  )
}
