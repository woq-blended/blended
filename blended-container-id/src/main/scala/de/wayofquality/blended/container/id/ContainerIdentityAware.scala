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

package de.wayofquality.blended.container.id

import de.wayofquality.blended.akka.OSGIActor
import de.wayofquality.blended.container.context.ContainerContext

import scala.collection.convert.Wrappers.JPropertiesWrapper

trait ContainerIdentityAware { this : OSGIActor =>

  def containerProperties : Map[String, String] =
    invokeService[ContainerIdentifierService, Map[String, String]] {
      case Some(idSvc) => JPropertiesWrapper(idSvc.getProperties).toMap
      case _ => Map.empty
    }

  def containerUUID : Option[String] = 
    invokeService[ContainerIdentifierService, Option[String]] {
      case Some(idSvc) => Some(idSvc.getUUID)
      case None => None
    }

  def containerContext : Option[ContainerContext] =
    invokeService[ContainerIdentifierService, Option[ContainerContext]] {
      case Some(idSvc) => Some(idSvc.getContainerContext)
      case None => None
    }
}
