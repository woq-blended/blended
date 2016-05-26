package blended.security.internal

import blended.container.context.ContainerIdentifierService
import domino.DominoActivator
import org.apache.shiro.SecurityUtils
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.config.IniSecurityManagerFactory
import org.apache.shiro.util.ThreadContext
import org.slf4j.LoggerFactory

class SecurityActivator extends DominoActivator {

  private[this] val log = LoggerFactory.getLogger(classOf[SecurityActivator])

  whenBundleActive {

    whenServicePresent[ContainerIdentifierService] { idSvc =>

      val factory = new IniSecurityManagerFactory(s"file:${idSvc.getContainerContext().getContainerConfigDirectory()}/shiro.ini")
      val secMgr = factory.getInstance()

      ThreadContext.bind(secMgr)

      val currentUser = SecurityUtils.getSubject()

      val session = currentUser.getSession()
      session.setAttribute("foo", "bar")

      log.debug(s"Value of foo is [${session.getAttribute("foo")}]")
      val token = new UsernamePasswordToken("root", "secret")

      log.debug(s"User [${currentUser.getPrincipal()}] is logged in : [${currentUser.isAuthenticated()}]")
      currentUser.login(token)

      log.debug(s"User [${currentUser.getPrincipal()}] is logged in : [${currentUser.isAuthenticated()}]")
      log.debug(s"User [${currentUser.getPrincipal()}] has role admin: [${currentUser.hasRole("admin")}]")
      log.debug(s"Value of foo is [${session.getAttribute("foo")}]")

      currentUser.logout()
      log.debug(s"User [${currentUser.getPrincipal()}] is logged in : [${currentUser.isAuthenticated()}]")
      log.debug(s"User [${currentUser.getPrincipal()}] has role admin: [${currentUser.hasRole("admin")}]")

      log.info(s"Instance of [${secMgr.getClass().getName()}] started.")
      secMgr.providesService[org.apache.shiro.mgt.SecurityManager]

    }

  }

}
