import sbt._

object BlendedTestsupport extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.testsupport",
    "Some test helper classes."
  ) {
    override val osgi = false

    override val libDeps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaTestkit,
      Dependencies.akkaCamel,
      Dependencies.scalatest,
      Dependencies.junit
    )
  }
  override  val project  = helper.baseProject.dependsOn(
    BlendedUtil.project,
    BlendedUtilLogging.project,
    BlendedSecurityBoot.project
  )
}
