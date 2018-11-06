package blended.jms.bridge

import scala.util.Try

class NoInternalBridgeProviderException extends Exception("No internal provider configured in Jms Bridge")
class BridgeProviderNotFoundException(vendor: String, provider: String)
  extends Exception(s"No bridge provider found for [$vendor:$provider]")

class BridgeProviderRegistry(
  provider : List[BridgeProviderConfig]
) {

  def internalProvider : Try[BridgeProviderConfig] = Try {
    provider.find(_.internal) match {
      case None => throw new NoInternalBridgeProviderException
      case Some(p) => p
    }
  }

  def mandatoryProvider(v: String, p: String) : Try[BridgeProviderConfig] = Try {
    jmsProvider(v,p) match {
      case None => throw new BridgeProviderNotFoundException(v,p)
      case Some(cfg) => cfg
    }
  }

  def jmsProvider(v: String, p: String): Option[BridgeProviderConfig] =
    provider.find(bp => bp.vendor == v && bp.provider == p)
}
