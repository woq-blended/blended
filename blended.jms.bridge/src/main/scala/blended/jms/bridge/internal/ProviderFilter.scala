package blended.jms.bridge.internal

import blended.jms.utils.ProviderAware
import blended.util.logging.Logger

import scala.reflect.ClassTag

object ProviderFilter {
  def apply(vendor: String): ProviderFilter = apply(vendor, None)
  def apply(vendor: String, provider: String) : ProviderFilter = apply(vendor, Some(provider))
  def apply(vendor: String, provider: Option[String]): ProviderFilter = new ProviderFilter(vendor, provider)
}

class ProviderFilter(vendor: String, provider: Option[String]) {

  private[this] val log = Logger[ProviderFilter]

  def matches[T <: ProviderAware](p : T) : Boolean = {

    val result = (vendor, provider) match {
      // we need the vendor to match
      case (p.vendor, s) => s match {
        // The provider to search for is not specified => match
        case None => true
        // The provider to search for matches exactly => match
        case Some(p.provider) => true
        // if the provider to search for is a regex => matches if the regex matches
        case (Some(pattern)) => p.provider.matches(pattern)
      }

      // If the vendor does not match, nothing matches
      case (_, _) => false
    }

    log.debug(s"Checking Provider [${p.vendor},${p.provider}] with [$vendor, $provider], matched [$result]")
    result
  }

  def listProviderFilter[T <: ProviderAware](l : List[T], vendor: String, provider: Option[String])(implicit classTag: ClassTag[T]) : List[T] =
    l.filter(matches)

  def singleProviderFilter[T <: ProviderAware](l: List[T], vendor: String, provider: String)(implicit evidence : ClassTag[T]) : Option[T] = {

    l.filter(matches) match {
      case Nil =>
        l.filter { p => p.vendor == vendor && p.provider.isEmpty } match {
          case Nil =>
            log.warn(s"No config entry found for [$vendor, $provider]")
            None
          case head :: tail =>
            if (tail.nonEmpty) log.warn(s"Config entry for [$vendor] is not unique.")
            Some(head)
        }
      case head :: tail =>
        if (tail.nonEmpty) log.warn(s"Config entry for [$vendor, $provider] is not unique.")
        Some(head)
    }
  }
}
