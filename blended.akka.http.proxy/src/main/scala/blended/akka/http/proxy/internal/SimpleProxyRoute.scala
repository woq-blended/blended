package blended.akka.http.proxy.internal

import akka.actor.ActorSystem
import javax.net.ssl.SSLContext

class SimpleProxyRoute(
  override val proxyConfig: ProxyTarget,
  override val actorSystem: ActorSystem,
  override val sslContext: Option[SSLContext] = None
) extends ProxyRoute
