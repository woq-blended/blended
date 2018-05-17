package blended.security.internal

import blended.security.boot.BlendedLoginModule
import blended.security.boot.BlendedLoginModule.{propBundle, propModule}
import com.typesafe.config.Config
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag
import javax.security.auth.login.{AppConfigurationEntry, Configuration}

import scala.collection.JavaConverters._

class BlendedConfiguration(bundleName: String, loginModuleClassName: String, cfg: Config) extends Configuration {

  override def getAppConfigurationEntry(name: String): Array[AppConfigurationEntry] = {

    val options : Map[String, Any] = Map(
      propBundle -> bundleName,
      propModule -> loginModuleClassName,
      "config" -> cfg
    )

    val entry = new AppConfigurationEntry(classOf[BlendedLoginModule].getName(), LoginModuleControlFlag.REQUISITE, options.asJava)

    Array(entry)
  }
}
