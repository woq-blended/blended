package blended.updater.config

import scala.collection.immutable

case class ContainerRegistryResponseOK(id: String, actions: immutable.Seq[UpdateAction] = immutable.Seq())
