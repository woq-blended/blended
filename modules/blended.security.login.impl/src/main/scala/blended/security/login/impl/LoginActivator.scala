package blended.security.login.impl

import blended.akka.ActorSystemWatching
import blended.security.BlendedPermissionManager
import blended.security.login.api.TokenStore
import domino.DominoActivator

class LoginActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>
      whenServicePresent[BlendedPermissionManager] { mgr =>

        val tokenHandler = RSATokenHandler()
        val store = new SimpleTokenStore(mgr, tokenHandler, osgiCfg.system)

        store.providesService[TokenStore]
      }
    }
  }
}
