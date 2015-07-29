package blended.updater.config

class EnvPropertyProvider extends PropertyProvider {

  override def provide(key: String): Option[String] = sys.env.get(key)

}