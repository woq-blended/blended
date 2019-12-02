import blended.sbt.Dependencies
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import sbt._

object BlendedSecurityCrypto extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.crypto",
    description = "Provides classes and mainline for encrypting / decrypting arbitrary Strings.",
    deps = Seq(
      Dependencies.cmdOption,

      Dependencies.scalatest % Test,
      Dependencies.scalacheck % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.osLib % Test
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
