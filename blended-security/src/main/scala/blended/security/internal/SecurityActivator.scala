package blended.security.internal

import blended.container.context.ContainerIdentifierService
import domino.DominoActivator
import org.apache.shiro.cache.MemoryConstrainedCacheManager
import org.apache.shiro.config.IniSecurityManagerFactory

class SecurityActivator extends DominoActivator {

  whenBundleActive {

    whenServicePresent[ContainerIdentifierService] { idSvc =>

      val factory = new IniSecurityManagerFactory(s"file:${idSvc.getContainerContext().getContainerConfigDirectory()}/shiro.ini")
      val secMgr = factory.getInstance()

      secMgr.providesService[org.apache.shiro.mgt.SecurityManager]


    }

  }

}
