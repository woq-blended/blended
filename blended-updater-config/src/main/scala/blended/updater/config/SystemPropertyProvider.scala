package blended.updater.config

class SystemPropertyProvider extends PropertyProvider {

  override def provide(key: String): Option[String] = sys.props.get(key)

}