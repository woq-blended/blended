package blended.streams.internal

import blended.container.context.api.ContainerIdentifierService
import blended.streams.BlendedStreamsConfig
import blended.util.config.Implicits._
import blended.util.logging.Logger
import com.typesafe.config.Config

class BlendedStreamsConfigImpl(idSvc : ContainerIdentifierService, cfg : Config) extends BlendedStreamsConfig {

  private val log : Logger = Logger[BlendedStreamsConfigImpl]

  override val transactionShard: Option[String] = {
    val shard : Option[String] =
      cfg.getStringOption("transactionShard").flatMap { s => idSvc.resolvePropertyString(s).map(_.toString()).toOption }

    log.info(s"Transaction Shard is [$shard]")

    shard
  }
}
