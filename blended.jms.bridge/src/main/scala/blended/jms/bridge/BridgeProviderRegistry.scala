package blended.jms.bridge

import scala.util.Try

class BridgeProviderRegistry(
  provider : List[BridgeProviderConfig]
) {

  def internalProvider : Try[BridgeProviderConfig] = Try {
    provider.find(_.internal) match {
      case None => throw new Exception("No internal provider configured in Jms Bridge")
      case Some(p) => p
    }
  }

  def jmsProvider(v: String, p: String): Option[BridgeProviderConfig] =
    provider.find(bp => bp.vendor == v && bp.provider == p)
}
