package blended.jms.utils

object Reconnect {
  def apply(cf : IdAwareConnectionFactory, e: Option[Throwable]): Reconnect =
    new Reconnect(cf.vendor, cf.provider, e)
}

case class Reconnect(vendor: String, provider: String, e: Option[Throwable])
