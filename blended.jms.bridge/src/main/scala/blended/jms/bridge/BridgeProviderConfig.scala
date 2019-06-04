package blended.jms.bridge

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{JmsDestination, ProviderAware}
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.util.Try

case class BridgeProviderConfig(
  vendor : String,
  provider : String,
  internal : Boolean,
  inbound : JmsDestination,
  outbound : JmsDestination,
  retry : Option[JmsDestination],
  retryFailed : JmsDestination,
  errors : JmsDestination,
  transactions : JmsDestination,
  cbes : JmsDestination
) extends ProviderAware {

  override def toString : String =
    s"${getClass().getSimpleName()}(vendor=$vendor, provider=$provider, internal=$internal, errors=$errors, transactions=$transactions cbe=$cbes)"

  def osgiBrokerFilter : String = s"(&(vendor=$vendor)(provider=$provider))"

}

object BridgeProviderConfig {

  def create(idSvc : ContainerIdentifierService, cfg : Config) : Try[BridgeProviderConfig] = Try {

    def resolve(value : String) : String = idSvc.resolvePropertyString(value).map(_.toString()).get

    val vendor = resolve(cfg.getString("vendor"))
    val provider = resolve(cfg.getString("provider"))

    val errorDest = resolve(cfg.getString("errors", "blended.error"))
    val eventDest = resolve(cfg.getString("transactions", "blended.transaction"))
    val cbeDest = resolve(cfg.getString("cbes", "blended.cbe"))

    val retryDest : Option[JmsDestination] = cfg.getStringOption("retry").map(resolve).map(s => JmsDestination.create(s).get)
    val retryFailed : JmsDestination = JmsDestination.create(cfg.getString("retryFailed", errorDest)).get

    val inbound = s"${cfg.getString("inbound")}"
    val outbound = s"${cfg.getString("outbound")}"

    val internal = cfg.getBoolean("internal", false)

    BridgeProviderConfig(
      vendor = vendor,
      provider = provider,
      internal = internal,
      inbound = JmsDestination.create(inbound).get,
      outbound = JmsDestination.create(outbound).get,
      retry = retryDest,
      retryFailed = retryFailed,
      errors = JmsDestination.create(errorDest).get,
      transactions = JmsDestination.create(eventDest).get,
      cbes = JmsDestination.create(cbeDest).get
    )
  }
}
