package blended.testsupport.security

import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag
import javax.security.auth.login.{AppConfigurationEntry, Configuration}

import scala.collection.JavaConverters._

class DummyLoginConfiguration extends Configuration {

  private[this] val options : Map[String, String] = Map.empty

  override def getAppConfigurationEntry(name : String) : Array[AppConfigurationEntry] = {

    val entry = new AppConfigurationEntry(classOf[DummyLoginModule].getName(), LoginModuleControlFlag.SUFFICIENT, options.asJava)

    Array(entry)
  }
}
