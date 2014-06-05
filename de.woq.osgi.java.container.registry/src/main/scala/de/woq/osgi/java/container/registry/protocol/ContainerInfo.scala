/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.java.container.registry.protocol

import de.woq.osgi.akka.persistence.protocol._
import scala.collection.mutable

case class ContainerInfo (containerId : String, properties : Map[String, String]) extends DataObject(containerId) {
  override def persistenceProperties: PersistenceProperties = {
    var builder =
      new mutable.MapBuilder[String, PersistenceProperty, mutable.Map[String, PersistenceProperty]](mutable.Map.empty)

    builder ++= super.persistenceProperties
    properties.foreach { case (k, v) => builder += (k -> StringProperty(v)) }

    builder.result().toMap
  }
}

case class UpdateContainerInfo (info: ContainerInfo)
case class ContainerRegistryResponseOK (id: String)


