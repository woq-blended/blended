package blended.security.internal

import blended.domino.TypesafeConfigWatching
import blended.security.LDAPLoginModule
import blended.security.boot.BlendedLoginModule
import domino.DominoActivator
import javax.security.auth.login.Configuration

class SecurityActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {

    whenTypesafeConfigAvailable{ (cfg, idSvc) =>

      log.info("Initialising Blended Login Module")
      BlendedLoginModule.init(bundleContext)

      val loginModuleClassName = classOf[LDAPLoginModule].getName()

      log.info(s"Initialising security manager with LoginModule [$loginModuleClassName].")
      Configuration.setConfiguration(
        new BlendedConfiguration(
          bundleName = bundleContext.getBundle().getSymbolicName(),
          loginModuleClassName = loginModuleClassName,
          cfg
        ))
    }
  }
}
