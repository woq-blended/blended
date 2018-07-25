package blended.security

import javax.security.auth.login.LoginException
import blended.util.config.Implicits._

class ConfigLoginModule extends AbstractLoginModule {

  override protected val moduleName: String = "simple"

  @throws[LoginException]
  override protected def doLogin(): Boolean = {

    val (user, pwd) = extractCredentials()

    if (!loginConfig.hasPath(user)) {
      throw new LoginException(s"User [$user] not found in module [$moduleName]")
    }

    if (pwd != loginConfig.getConfig(user).getString("pwd", "")) {
      throw new LoginException(s"Wrong credentials for user [$user] in module [$moduleName]")
    }

    loggedInUser = Some(user)
    true
  }

  override protected def getGroups(user: String): List[String] = {
    loginConfig.getConfig(user).getStringList("groups", List.empty)
  }
}
