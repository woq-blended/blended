import sbt._

object BlendedJmsBridge extends ProjectFactory {

  private[this] val helper : ProjectSettings = new ProjectSettings(
    projectName = "blended.jms.bridge",
    description = "A generic JMS bridge to connect the local JMS broker to en external JMS"
  )

  override val project = helper.baseProject.dependsOn(

  )
}
