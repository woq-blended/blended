package blended.streams

import blended.container.context.api.ContainerIdentifierService
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.concurrent.duration._

object BlendedStreamsConfig {

  def create(idSvc : ContainerIdentifierService) : BlendedStreamsConfig = {
    val cfg : Config = idSvc.containerContext.getContainerConfig.getConfig("blended.streams")
    create(idSvc, cfg)
  }

  def create(idSvc : ContainerIdentifierService, cfg : Config) : BlendedStreamsConfig = BlendedStreamsConfig(
    transactionShard = cfg.getStringOption("transactionShard"),
    minDelay  = cfg.getDuration("minDelay", 5.seconds),
    maxDelay = cfg.getDuration("maxDelay", 1.minute),
    exponential = cfg.getBoolean("exponential", true),
    random = cfg.getDouble("random", 0.2),
    onFailureOnly = cfg.getBoolean("onFailureOnly", true),
    resetAfter = cfg.getDuration("resetAfter", 10.seconds)
  )
}

case class BlendedStreamsConfig(
  transactionShard : Option[String],
  minDelay : FiniteDuration,
  maxDelay : FiniteDuration,
  exponential : Boolean,
  random : Double,
  onFailureOnly : Boolean,
  resetAfter : FiniteDuration
) {

  override def toString: String = s"${getClass().getName()}(shard=$transactionShard, minDelay=$minDelay, maxDelay=$maxDelay, " +
    s"exponential=$exponential), random=$random, failureOnly=$onFailureOnly, resetAfter=$resetAfter)"

}

