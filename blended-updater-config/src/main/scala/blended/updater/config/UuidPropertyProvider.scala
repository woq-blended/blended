package blended.updater.config

import java.util.UUID

class UuidPropertyProvider(key: String) extends PropertyProvider {

  override def provide(key: String): Option[String] =
    if (this.key == key) Option(UUID.randomUUID().toString())
    else None

}