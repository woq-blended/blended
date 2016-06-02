package blended.security.internal

import javax.security.auth.login.Configuration

import blended.container.context.ContainerIdentifierService
import blended.security.ShiroLoginModule
import blended.security.boot.BlendedLoginModule
import domino.DominoActivator
import org.apache.shiro.config.IniSecurityManagerFactory
import org.apache.shiro.util.ThreadContext
import org.slf4j.LoggerFactory

class SecurityActivator extends DominoActivator {

  private[this] val log = LoggerFactory.getLogger(classOf[SecurityActivator])

  whenBundleActive {

    whenServicePresent[ContainerIdentifierService] { idSvc =>

      log.info("Initialising Blended Login Module")
      BlendedLoginModule.init(bundleContext)

      log.info("Initialising security manager.")
      Configuration.setConfiguration(new BlendedConfiguration(bundleContext))

      val factory = new IniSecurityManagerFactory(s"file:${idSvc.getContainerContext().getContainerConfigDirectory()}/shiro.ini")
      val secMgr = factory.getInstance()
      ShiroLoginModule.setSecurityManager(secMgr)

      ThreadContext.bind(secMgr)

      log.info(s"Instance of [${secMgr.getClass().getName()}] started.")
      secMgr.providesService[org.apache.shiro.mgt.SecurityManager]
    }
  }
}
