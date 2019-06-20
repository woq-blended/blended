import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import phoenix.ProjectFactory
import sbt._

object BlendedSecurityCrypto extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.security.crypto"
    override val description : String = "Provides classes and mainline for encrypting / decrypting arbitrary Strings."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.cmdOption,

      Dependencies.scalatest % Test,
      Dependencies.scalacheck % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
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
