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

package de.wayofquality.blended.akka.internal

import akka.actor.ActorSystem
import akka.camel.CamelExtension
import org.apache.camel.Component
import org.helgoboss.domino.DominoActivator
import org.helgoboss.domino.service_watching.ServiceWatcherEvent.{AddingService, ModifiedService, RemovedService}
import org.slf4j.LoggerFactory

class CamelComponentTracker(da: DominoActivator) {
  
  private[CamelComponentTracker] val log = LoggerFactory.getLogger(classOf[CamelComponentTracker])
  
  val idProperty = "CamelComponentId"
  var components : Map[String, Component] = Map.empty

  da.whenBundleActive {
    da.whenServicePresent[ActorSystem] { system =>
      da.watchServices[Component] {
        case AddingService(s, context) =>
          if (context.ref.getPropertyKeys.contains(idProperty)) {
            val id = context.ref.getProperty(idProperty).asInstanceOf[String]
            addComponent(system, id, s)
          }
        case ModifiedService(s, context) =>
          if (context.ref.getPropertyKeys.contains(idProperty)) {
            val id = context.ref.getProperty(idProperty).asInstanceOf[String]
            removeComponent(system, id)
            addComponent(system, id, s)
          }
        case RemovedService(s, context) =>
          if (context.ref.getPropertyKeys.contains(idProperty)) {
            val id = context.ref.getProperty(idProperty).asInstanceOf[String]
            removeComponent(system, id)
          }
      }
    }
  }
  
  private[CamelComponentTracker] def addComponent(system: ActorSystem, id: String, component: Component) : Unit = {
    log info s"Adding Component of type [${component.getClass.getName}] with component Id [$id]."
    CamelExtension(system).context.addComponent(id, component)
    components += (id -> component)
  }

  private[CamelComponentTracker] def removeComponent(system: ActorSystem, id: String) : Unit = {
    if (components.get(id).isDefined) {
      log info s"Removing Component with component Id [$id]."
      CamelExtension(system).context.removeComponent(id)
      components = components.filter { case (k, v) => k != id }
    }
  }
}
