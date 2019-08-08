package blended.streams.internal

import blended.container.context.api.ContainerIdentifierService
import blended.streams.BlendedStreamsConfig
import blended.util.config.Implicits._
import blended.util.logging.Logger
import com.typesafe.config.Config
import scala.concurrent.duration._

import scala.concurrent.duration.FiniteDuration

object BlendedStreamsConfigImpl {

  def apply(idSvc : ContainerIdentifierService) : BlendedStreamsConfig = {
    val cfg : Config = idSvc.containerContext.getContainerConfig.getConfig("blended.streams")
    apply(idSvc, cfg)
  }

  def apply(idSvc : ContainerIdentifierService, cfg : Config) : BlendedStreamsConfig =
    new BlendedStreamsConfigImpl(idSvc, cfg)
}

class BlendedStreamsConfigImpl(idSvc : ContainerIdentifierService, cfg : Config) extends BlendedStreamsConfig {

  private val log : Logger = Logger[BlendedStreamsConfigImpl]

  override val transactionShard: Option[String] = {
    val shard : Option[String] =
      cfg.getStringOption("transactionShard").flatMap { s => idSvc.resolvePropertyString(s).map(_.toString()).toOption }

    log.info(s"Transaction Shard is [$shard]")

    shard
  }

  override val minDelay : FiniteDuration = cfg.getDuration("minDelay", 5.seconds)
  override val maxDelay : FiniteDuration = cfg.getDuration("maxDelay", 1.minute)
  override val exponential : Boolean = cfg.getBoolean("exponential", true)
  override val random : Double = cfg.getDouble("random", 0.2)
  override val onFailureOnly : Boolean = cfg.getBoolean("onFailureOnly", true)
  override val resetAfter : FiniteDuration = cfg.getDuration("resetAfter", 10.seconds)
}
