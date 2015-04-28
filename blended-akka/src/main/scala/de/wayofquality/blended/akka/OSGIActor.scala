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
import akka.pattern.ask
import akka.util.Timeout
import de.wayofquality.blended.akka.protocol._
import org.helgoboss.domino.service_consuming.ServiceConsuming
import de.wayofquality.blended.akka.internal.ConfigLocator
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag
import de.wayofquality.blended.akka.internal.ConfigDirectoryProvider
import de.wayofquality.blended.container.context.ContainerContext
import com.typesafe.config.Config

trait OSGIActor extends Actor 
  with ActorLogging 
  with ServiceConsuming
  with ConfigLocator
  with ConfigDirectoryProvider { this: BundleName =>

  implicit val timeout = new Timeout(500.millis)
  implicit val ec = context.dispatcher
  
  override def fallback : Option[Config] = Some(context.system.settings.config)
  
  override def configDirectory = 
    withService[ContainerContext, String] { 
      case Some(ctxt) => ctxt.getContainerConfigDirectory
      case _ => s"${System.getProperty("karaf.home")}/etc"
    }

  def bundleActor(bundleName : String) : Future[ActorRef] = {
    log debug s"Trying to resolve bundle actor [$bundleName]"
    context.actorSelection(s"/user/$bundleName").resolveOne().fallbackTo(Future(context.system.deadLetters))
  }

  def osgiFacade = bundleActor(BlendedAkkaConstants.osgiFacadePath)

  def invokeService[I <: AnyRef : ClassTag, T](f: Option[I] => T) : T = withService[I,T] { service => f(service) }
}
