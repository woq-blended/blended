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

import akka.actor.{Actor, ActorLogging}
import akka.camel.CamelExtension
import de.wayofquality.blended.akka.protocol.{TrackerModifiedService, TrackerRemovedService, TrackerAddingService}
import de.wayofquality.blended.akka.{BundleName, OSGIActor}
import org.apache.camel.Component

class CamelComponentTracker extends OSGIActor with ActorLogging with BundleName {
  
  val idProperty = "CamelComponentId"
  var components : Map[String, Component] = Map.empty

  override def bundleSymbolicName = "de.wayofquality.blended.akka"

  override def preStart(): Unit = {
    super.preStart()
    log info "Starting Camel component Tracker"
    createTracker[Component](classOf[Component])
  }

  def receive = {

    case TrackerAddingService(svcRef, svc) => 
      if (svcRef.getPropertyKeys.contains("CamelComponentId")) {
        val component = svc.asInstanceOf[Component]
        val id = svcRef.getProperty(idProperty).asInstanceOf[String]
        addComponent(id, component)
      }
      
    case TrackerRemovedService(svcRef, svc) =>
      if (svcRef.getPropertyKeys.contains("CamelComponentId")) {
        val component = svc.asInstanceOf[Component]
        val id = svcRef.getProperty(idProperty).asInstanceOf[String]
        removeComponent(id, component)
      }

    case TrackerModifiedService(svcRef, svc) =>
      if (svcRef.getPropertyKeys.contains("CamelComponentId")) {
        val component = svc.asInstanceOf[Component]
        val id = svcRef.getProperty(idProperty).asInstanceOf[String]
        removeComponent(id, component)
        addComponent(id, component)
      }
  }

  private[this] def addComponent(id: String, component: Component) : Unit = {
    log info s"Adding Component of type [${component.getClass.getName}] with component Id [$id]."
    CamelExtension(context.system).context.addComponent(id, component)
    components += (id -> component)
  }

  private[this] def removeComponent(id: String, component: Component) : Unit = {
    if (components.get(id).isDefined) {
      log info s"Removing Component of type [${component.getClass.getName}] with component Id [$id]."
      CamelExtension(context.system).context.addComponent(id, component)
      components = components.filter { case (k, v) => k != id }
    }
  }
}
