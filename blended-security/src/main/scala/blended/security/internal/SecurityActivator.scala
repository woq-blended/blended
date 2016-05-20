package blended.security.internal

import domino.DominoActivator
import org.apache.shiro.cache.MemoryConstrainedCacheManager
import org.apache.shiro.config.IniSecurityManagerFactory

class SecurityActivator extends DominoActivator {

  whenBundleActive {

    val cacheMgr = new MemoryConstrainedCacheManager()

    val factory = new IniSecurityManagerFactory("classpath:/shiro.ini")
    val secMgr = factory.getInstance()


    secMgr.providesService[org.apache.shiro.mgt.SecurityManager]
  }

}
