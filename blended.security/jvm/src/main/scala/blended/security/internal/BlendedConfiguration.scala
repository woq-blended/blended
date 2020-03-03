package blended.security.internal

import blended.container.context.api.ContainerContext
import blended.security.boot.BlendedLoginModule
import blended.security.boot.BlendedLoginModule.{propBundle, propModule}
import com.typesafe.config.Config
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag
import javax.security.auth.login.{AppConfigurationEntry, Configuration}

import scala.collection.JavaConverters._

object BlendedConfiguration {
  val configProp = "config"
  val ctCtxtProp = "ctCtxt"
}

class BlendedConfiguration(
  bundleName : String,
  loginModuleClassName : String,
  cfg : Config,
  ctCtxt : ContainerContext
) extends Configuration {

  import BlendedConfiguration._

  override def getAppConfigurationEntry(name : String) : Array[AppConfigurationEntry] = {

    val options : Map[String, Any] = Map(
      propBundle -> bundleName,
      propModule -> loginModuleClassName,
      configProp -> cfg,
      ctCtxtProp -> ctCtxt
    )

    val entry = new AppConfigurationEntry(classOf[BlendedLoginModule].getName(), LoginModuleControlFlag.REQUISITE, options.asJava)

    Array(entry)
  }
}
