/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.wayofquality.blended.container.registry.internal

import akka.actor.ActorRef
import de.wayofquality.blended.akka.{OSGIActor, OSGIActorConfig}
import de.wayofquality.blended.container.registry.protocol._
import de.wayofquality.blended.persistence.protocol.StoreObject


object ContainerRegistryImpl {
  def apply(cfg: OSGIActorConfig) = new ContainerRegistryImpl(cfg)
}

class ContainerRegistryImpl(cfg: OSGIActorConfig) extends OSGIActor(cfg) {

  implicit private val eCtxt = context.system.dispatcher

  def receive = {
    case UpdateContainerInfo(info) =>
      log debug s"Received ${info.toString}"

      bundleActor("de.wayofquality.blended.persistence").map {
        case actor : ActorRef =>
          log.debug("Storing Container Information")
          actor ! StoreObject(info)
        case dlq if dlq == context.system.deadLetters => log.debug("Persistence manager not available")
      }

      sender ! ContainerRegistryResponseOK(info.containerId)
  }
}
