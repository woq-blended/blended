import blended.sbt.Dependencies
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import phoenix.ProjectFactory
import sbt._

object BlendedSecurityCrypto extends ProjectFactory {

  object config extends ProjectSettings {
    override val projectName = "blended.security.crypto"
    override val description = "Provides classes and mainline for encrypting / decrypting arbitrary Strings."

    override def deps = Seq(
      Dependencies.cmdOption,

      Dependencies.scalatest % Test,
      Dependencies.scalacheck % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle = super.bundle.copy(
      importPackage = Seq(
        "de.tototec.cmdoption;resolution:=optional"
      ),
      exportPackage = Seq(
        projectName
      )
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO"
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = super.dependsOn ++ Seq(
      BlendedTestsupport.project % Test
    )
  }

}
