package blended.security.internal

import blended.domino.TypesafeConfigWatching
import blended.security.boot.BlendedLoginModule
import blended.security.{BlendedPermissionManager, ConfigLoginModule, LDAPLoginModule}
import blended.util.config.Implicits._
import blended.util.logging.Logger
import domino.DominoActivator
import javax.security.auth.login.Configuration

class SecurityActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = Logger[SecurityActivator]

  whenBundleActive {

    whenTypesafeConfigAvailable { (cfg, ctContext) =>
      val symName = bundleContext.getBundle().getSymbolicName()
      val module = cfg.getString("module", "simple")

      log.info(s"Initialising Blended Login Module with implementation [$module]")

      val loginModuleClassName = module match {
        case "ldap"   => classOf[LDAPLoginModule].getName()
        case "simple" => classOf[ConfigLoginModule].getName()
        case o        => throw new Exception(s"Unknown login module implementation : [$o]")
      }

      if (!cfg.hasPath(module)) {
        throw new Exception(s"Configuration is missing config section [$symName.$module]")
      }

      log.info(s"Initialising security manager with LoginModule [$loginModuleClassName].")

      Configuration.setConfiguration(
        new BlendedConfiguration(
          bundleName = symName,
          loginModuleClassName = loginModuleClassName,
          cfg = cfg.getConfig(module),
          ctCtxt = ctContext
        )
      )

      BlendedLoginModule.init(bundleContext)

      val permissionManager = if (cfg.hasPath("permissions")) {
        log.info("Using ConfigPermissionManager")
        new ConfigPermissionManager(cfg.getObject("permissions"))
      } else {
        log.info("Using GroupPermissionManager")
        new GroupPermissionManager()
      }

      permissionManager.providesService[BlendedPermissionManager]
    }
  }
}
