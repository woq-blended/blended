package blended.security.internal

import javax.security.auth.login.Configuration

import blended.container.context.api.ContainerIdentifierService
import blended.security.ShiroLoginModule
import blended.security.boot.BlendedLoginModule
import domino.DominoActivator
import org.apache.shiro.config.IniSecurityManagerFactory
import org.apache.shiro.util.ThreadContext

class SecurityActivator extends DominoActivator {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {

    whenServicePresent[ContainerIdentifierService] { idSvc =>

      log.info("Initialising Blended Login Module")
      BlendedLoginModule.init(bundleContext)

      log.info("Initialising security manager.")
      Configuration.setConfiguration(
        new BlendedConfiguration(
          bundleName = bundleContext.getBundle().getSymbolicName(),
          loginModuleClassName = classOf[ShiroLoginModule].getName()
        ))

      try {
        val factory = new IniSecurityManagerFactory(s"file:${idSvc.containerContext.getProfileConfigDirectory()}/shiro.ini")
        val secMgr = factory.getInstance()
        ShiroLoginModule.setSecurityManager(secMgr)

        ThreadContext.bind(secMgr)

        log.info(s"Instance of [${secMgr.getClass().getName()}] started.")
        secMgr.providesService[org.apache.shiro.mgt.SecurityManager]
      } catch {
        case t : Throwable =>
          log.error(t.getMessage())
          log.debug(t)(t.getMessage())

          throw t
      }
    }
  }
}
