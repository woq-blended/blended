import blended.sbt.Dependencies
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import sbt._

object BlendedSecurityCrypto extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.crypto",
    description = "Provides classes and mainline for encrypting / decrypting arbitrary Strings.",
    deps = Seq(
      Dependencies.cmdOption,

      Dependencies.scalatest % "test",
      Dependencies.scalacheck % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      importPackage = Seq(
        "de.tototec.cmdoption;resolution:=optional"
      ),
      exportPackage = Seq(
        b.bundleSymbolicName
      )
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      Test / testlogDefaultLevel := "INFO"
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedTestsupport.project % "test"
  )
}
