package blended.jms.bridge.internal

import blended.util.logging.Logger

import scala.reflect.ClassTag

object ProviderFilter {

  private[this] val log = Logger[ProviderFilter.type]

  def listProviderFilter[T <: ProviderAware](l : List[T], vendor: String, provider: Option[String]) : List[T] = {

    val filter : T => Boolean = { p =>

      val result = p.vendor == vendor &&
        ((p.provider, provider) match {
          case (None, _) => true
          case (Some(pattern), Some(regName)) => regName.matches(pattern)
          case (_,_) => false
        })

      log.debug(s"Checking Provider [${p.vendor},${p.provider}] with [$vendor, $provider], active [$result]")
      result
    }

    l.filter(filter)
  }


  def singleProviderFilter[T <: ProviderAware](l: List[T], vendor: String, provider: String)(implicit evidence : ClassTag[T]) : Option[T] = {

    (l.filter { p => p.vendor == vendor && p.provider == Some(provider) }) match {
      case Nil =>
        l.filter { p => p.vendor == vendor && p.provider.isEmpty } match {
          case Nil =>
            log.warn(s"No config entry found for [$vendor, $provider]")
            None
          case head :: tail =>
            if (!tail.isEmpty) log.warn(s"Config entry for [$vendor] is not unique.")
            Some(head)
        }
      case head :: tail =>
        if (!tail.isEmpty) log.warn(s"Config entry for [$vendor, $provider] is not unique.")
        Some(head)
    }
  }
}
