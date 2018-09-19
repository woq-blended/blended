import sbt._

object BlendedActivemqClient extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.activemq.client",
    description = "An Active MQ Connection factory as a service",
    deps = Seq(
      Dependencies.activeMqClient
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.AmqClientActivator",
      exportPackage = Seq()
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project
  )
}
