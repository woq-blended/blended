import sbt._
import blended.sbt.Dependencies

object BlendedTestsupport extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    "blended.testsupport",
    "Some test helper classes.",
    osgi = false,
    deps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaTestkit,
      Dependencies.akkaCamel,
      Dependencies.camelCore,
      Dependencies.camelJms,
      Dependencies.scalatest,
      Dependencies.junit
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedUtil.project,
    BlendedUtilLogging.project,
    BlendedSecurityBoot.project
  )
}
