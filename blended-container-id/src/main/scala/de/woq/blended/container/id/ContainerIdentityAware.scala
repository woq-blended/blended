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

package de.woq.blended.container.id

import java.util.Properties

import de.woq.blended.akka.OSGIActor
import de.woq.blended.akka.protocol.ServiceResult
import de.woq.blended.container.context.ContainerContext

import scala.collection.convert.Wrappers.JPropertiesWrapper
import scala.concurrent.Future

trait ContainerIdentityAware { this : OSGIActor =>

  def containerProperties : Future[Map[String, String]] = {
    (for {
      result <- invokeService(classOf[ContainerIdentifierService])(_.getProperties).mapTo[ServiceResult[Properties]]
    } yield (result.result)).map { _ match {
      case None => Map.empty[String, String]
      case Some(p) => JPropertiesWrapper(p).toMap
    }}
  }

  def containerUUID : Future[Option[String]] = {
    for {
      result <- invokeService(classOf[ContainerIdentifierService])(_.getUUID).mapTo[ServiceResult[String]]
    } yield (result.result)
  }

  def containerContext : Future[Option[ContainerContext]] = {
    for {
      result <- invokeService(classOf[ContainerIdentifierService])(_.getContainerContext).mapTo[ServiceResult[ContainerContext]]
    } yield (result.result)
  }
}
