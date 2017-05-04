package blended.samples.spray.helloworld.internal

import org.apache.shiro.util.Destroyable
import org.apache.shiro.util.Initializable
import org.apache.shiro.web.env.DefaultWebEnvironment
import org.osgi.framework.BundleContext
import org.slf4j.LoggerFactory
import org.apache.shiro.mgt.SecurityManager
import org.apache.shiro.web.mgt.DefaultWebSecurityManager

class BlendedShiroWebEnv extends DefaultWebEnvironment with Initializable with Destroyable {

  private[this] val log = LoggerFactory.getLogger(classOf[BlendedShiroWebEnv])

  def bundleContext: BundleContext = {
    Option(getServletContext) match {
      case None =>
        sys.error("Could not init " + getClass() + ". No ServletContext.")
      case Some(sc) =>
        Option(sc.getAttribute("osgi-bundlecontext")) match {
          case None => sys.error("Could not find BundleContext. Attribute [osgi-bundlecontext] unefined in servlet context.")
          case Some(bc: BundleContext) => bc
          case Some(o) => sys.error(s"[${o.toString()}] is not of class BundleContext")
        }
    }
  }

  def init(): Unit = {
    log.info("initializing web environment for Shiro based on shared security manager (from OSGi registry)")
    val bc = bundleContext
    // Beware, we do not support disposal of SecurityManager
    // To do that, we need to be a normal bundle with service dependency
    Option(bc.getServiceReference(classOf[SecurityManager])).
      map(sr => bc.getService(sr)) match {
        case Some(secMgr: SecurityManager) =>
          //          val webSecMgr = new DelegatingWebSecurityManager(secMgr)
          val webSecMgr = new DefaultWebSecurityManager()
          log.debug("setting authenticator: {}", secMgr)
          webSecMgr.setAuthenticator(secMgr)
          log.debug("setting authorizer: {}", secMgr)
          webSecMgr.setAuthorizer(secMgr)
          setWebSecurityManager(webSecMgr)
        case None => sys.error("Could not find SecurityManager in bundle context.")
        case Some(s) => sys.error("Found class in bundle context which is not an instance of " + classOf[SecurityManager].getName() + ".")
      }
  }

}