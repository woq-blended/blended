package blended.updater.config

import blended.util.logging.Logger

abstract class PropertyProvider {

  private[this] val log = Logger[PropertyProvider]

  def doProvide(key: String) : Option[String] = {
    provide(key) match {
      case None => None
      case Some(s) =>
        log.info(s"Property [$key] has been resolved with [${getClass.getSimpleName}] : [$s]")
        Some(s)
    }
  }

  def provide(key: String): Option[String]
  
}