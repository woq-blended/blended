import sbt._

object BlendedActivemqDefaultbroker extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.activemq.defaultbroker",
    description = "An Active MQ broker instance",
    deps = Seq(),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.DefaultBrokerActivator",
      exportPackage = Seq()
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedActivemqBrokerstarter.project
  )
}
