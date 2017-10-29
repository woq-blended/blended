package blended.updater.config

trait PropertyProvider {

  def provide(key: String): Option[String]
  
}