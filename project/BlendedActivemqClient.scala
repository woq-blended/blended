import sbt._

object BlendedActivemqClient extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.activemq.client",
    description = "An Active MQ Connection factory as a service",
    deps = Seq(
      Dependencies.activeMqClient,
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.AmqClientActivator",
      exportPackage = Seq(b.bundleSymbolicName)
    )
  )

  override val project = helper.baseProject.dependsOn(
    
    BlendedDomino.project,
    BlendedUtil.project,
    BlendedUtilLogging.project,
    BlendedJmsUtils.project,
    BlendedAkka.project,
    BlendedStreams.project,
    
    BlendedTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test"
  )
}
