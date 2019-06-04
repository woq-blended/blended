package blended.streams.dispatcher.internal

import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.util.Try

object ProviderResolver {

  private[dispatcher] def providerFromConfig(
    idSvc : ContainerIdentifierService,
    registry : BridgeProviderRegistry,
    cfg : Config,
    vendorPath : String,
    providerPath : String
  ) : Try[Option[BridgeProviderConfig]] = Try {

    (cfg.getStringOption(vendorPath), cfg.getStringOption(providerPath)) match {
      case (Some(v), Some(p)) =>
        Some(getProvider(
          registry,
          idSvc.resolvePropertyString(v).map(_.toString()).get,
          idSvc.resolvePropertyString(p).map(_.toString()).get
        ).get)
      case (_, _) =>
        None
    }
  }

  private[dispatcher] def getProvider(
    registry : BridgeProviderRegistry,
    vendor : String,
    provider : String
  ) : Try[BridgeProviderConfig] = Try {
    registry.jmsProvider(vendor, provider) match {
      case None    => throw new Exception(s"Event provider [$vendor:$provider] is not configured in provider registry.")
      case Some(p) => p
    }
  }
}
