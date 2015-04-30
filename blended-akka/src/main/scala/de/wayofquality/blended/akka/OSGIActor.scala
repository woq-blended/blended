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

package de.wayofquality.blended.akka

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.util.Timeout
import com.typesafe.config.Config
import de.wayofquality.blended.container.context.ContainerContext
import de.wayofquality.blended.container.id.ContainerIdentifierService
import org.helgoboss.domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext

import scala.collection.convert.Wrappers.JPropertiesWrapper
import scala.concurrent.Future
import scala.concurrent.duration._

abstract class OSGIActor(actorConfig: OSGIActorConfig) 
  extends Actor
  with ActorLogging 
  with ServiceConsuming { this : BundleName =>

  private[this] implicit val timeout = new Timeout(500.millis)
  private[this] implicit val ec = context.dispatcher

  override protected def bundleContext: BundleContext = actorConfig.bundleContext

  def bundleActor(bundleName : String) : Future[ActorRef] = {
    log debug s"Trying to resolve bundle actor [$bundleName]"
    context.actorSelection(s"/user/$bundleName").resolveOne().fallbackTo(Future(context.system.deadLetters))
  }

  // Returns application.conf merged with the bundle specific config object
  protected def bundleActorConfig : Config = 
    context.system.settings.config.withValue(bundleSymbolicName, actorConfig.config.root())

  protected def containerProperties : Map[String, String] = JPropertiesWrapper(actorConfig.idSvc.getProperties()).toMap

  protected def containerUUID : String = actorConfig.idSvc.getUUID()

  protected def containerContext : ContainerContext = actorConfig.idSvc.getContainerContext()
}
