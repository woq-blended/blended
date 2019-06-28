package blended.jms.bridge

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{JmsDestination, ProviderAware}
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.util.Try
import blended.util.RichTry._

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

//noinspection NameBooleanParameters
object BridgeProviderConfig {

  def create(idSvc : ContainerIdentifierService, cfg : Config) : Try[BridgeProviderConfig] = Try {

    def resolve(value : String) : String = idSvc.resolvePropertyString(value).map(_.toString()).get

    val vendor = resolve(cfg.getString("vendor"))
    val provider = resolve(cfg.getString("provider"))

    val errorDest = resolve(cfg.getString("errors", "blended.error"))
    val eventDest = resolve(cfg.getString("transactions", "blended.transaction"))
    val cbeDest = resolve(cfg.getString("cbes", "blended.cbe"))

    val retryDest : Option[JmsDestination] = cfg.getStringOption("retry").map(resolve).map(s => JmsDestination.create(s).unwrap)
    val retryFailed : JmsDestination = JmsDestination.create(cfg.getString("retryFailed", errorDest)).unwrap

    val inbound = s"${cfg.getString("inbound")}"
    val outbound = s"${cfg.getString("outbound")}"

    val internal = cfg.getBoolean("internal", false)

    BridgeProviderConfig(
      vendor = vendor,
      provider = provider,
      internal = internal,
      inbound = JmsDestination.create(inbound).unwrap,
      outbound = JmsDestination.create(outbound).unwrap,
      retry = retryDest,
      retryFailed = retryFailed,
      errors = JmsDestination.create(errorDest).unwrap,
      transactions = JmsDestination.create(eventDest).unwrap,
      cbes = JmsDestination.create(cbeDest).unwrap
    )
  }
}
