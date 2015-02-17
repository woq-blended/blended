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

package de.woq.blended.akka.internal

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import de.woq.blended.akka.BlendedAkkaConstants
import de.woq.blended.container.context.ContainerContext
import BlendedAkkaConstants._
import de.woq.blended.akka.protocol._
import org.osgi.framework.BundleContext
import de.woq.blended.modules._

import scala.concurrent.duration._

object OSGIFacade {
  def apply()(implicit bundleContext : BundleContext) = new OSGIFacade()
}

class OSGIFacade(implicit bundleContext : BundleContext) extends Actor with ActorLogging {

  implicit val logger = context.system.log
  implicit val timeout = Timeout(1.second)
  implicit val ec = context.dispatcher

  var configLocator : ActorRef    = context.system.deadLetters
  var references : ActorRef       = context.system.deadLetters
  var trackers : ActorRef         = context.system.deadLetters
  var componentTracker : ActorRef = context.system.deadLetters
  
  override def preStart() :  Unit = {

    logger info "Creating Config Locator actor"
    configLocator = context.actorOf(Props(ConfigLocator(configDir)), configLocatorPath)

    logger info "Creating OSGI References handler"
    references = context.actorOf(Props(OSGIReferences()(bundleContext)), referencesPath)

    logger info "Creating OSGI Tracker handler"
    trackers = context.actorOf(Props(OSGIServiceTrackers(bundleContext)), trackersPath)
    
    logger info "Creating Camel Component Tracker"
    componentTracker = context.actorOf(Props[CamelComponentTracker], componentTrackerPath)
  }

  override def receive = LoggingReceive {

    case GetService(clazz) => references forward CreateReference(clazz)
    case createTracker : CreateTracker[_] => trackers forward createTracker
    case cfgRequest : ConfigLocatorRequest => configLocator forward cfgRequest
    case GetBundleActor(bundleId) =>
      (for {
        ref <- context.actorSelection(s"/user/$bundleId").resolveOne().mapTo[ActorRef]
      }  yield BundleActor(bundleId, ref)) pipeTo sender
  }

  private[OSGIFacade] def configDir = {

    val defaultConfigDir = System.getProperty("karaf.home") + "/etc"

    bundleContext findService classOf[ContainerContext] match {
      case Some(svcRef) => svcRef invokeService { ctx => ctx.getContainerConfigDirectory } match {
        case Some(s)  => s
        case _ => defaultConfigDir
      }
      case _ => defaultConfigDir
    }
  }
}
