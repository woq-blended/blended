package blended.container.registry.internal

import akka.actor.ActorRef
import blended.akka.{OSGIActor, OSGIActorConfig}
import blended.mgmt.base.json._
import blended.persistence.protocol._
import akka.actor.Props
import blended.updater.config.{ContainerRegistryResponseOK, UpdateContainerInfo}

object ContainerRegistryImpl {
  def props(cfg: OSGIActorConfig): Props = Props(new ContainerRegistryImpl(cfg))
}

class ContainerRegistryImpl(cfg: OSGIActorConfig) extends OSGIActor(cfg) {

  implicit private val eCtxt = context.system.dispatcher

  def receive = {
    case UpdateContainerInfo(info) =>
      log debug s"Received ${info.toString}"

      bundleActor("de.wayofquality.blended.persistence").map {
        case actor: ActorRef =>
          log.debug("Storing Container Information")
          // TODO FIXME: store container info
          // actor ! StoreObject(info)
        case dlq if dlq == context.system.deadLetters => log.debug("Persistence manager not available")
      }

      sender ! ContainerRegistryResponseOK(info.containerId)
  }
}
