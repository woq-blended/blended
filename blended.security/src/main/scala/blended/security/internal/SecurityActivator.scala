package blended.security.internal

import blended.domino.TypesafeConfigWatching
import blended.security.{BlendedPermissionManager, ConfigLoginModule, LDAPLoginModule}
import blended.security.boot.BlendedLoginModule
import domino.DominoActivator
import javax.security.auth.login.Configuration
import blended.util.config.Implicits._

class SecurityActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {

    whenTypesafeConfigAvailable{ (cfg, idSvc) =>
      val symName = bundleContext.getBundle().getSymbolicName()
      val module = cfg.getString("module", "simple")

      log.info(s"Initialising Blended Login Module with implementation [$module]")

      val loginModuleClassName = module match {
        case "ldap" => classOf[LDAPLoginModule].getName()
        case "simple" => classOf[ConfigLoginModule].getName()
        case o => throw new Exception(s"Unknown login module implementation : [$module]")
      }

      if (!cfg.hasPath(module)) {
        throw new Exception(s"Configuration is missing config section [$symName.$module]")
      }

      BlendedLoginModule.init(bundleContext)

      log.info(s"Initialising security manager with LoginModule [$loginModuleClassName].")

      Configuration.setConfiguration(
        new BlendedConfiguration(
          bundleName = symName,
          loginModuleClassName = loginModuleClassName,
          cfg = cfg.getConfig(module)
        )
      )

      new GroupPermissionManager().providesService[BlendedPermissionManager]
    }
  }
}
