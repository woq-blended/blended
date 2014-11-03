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

package de.woq.blended.akka

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import de.woq.blended.akka.protocol._

import akka.pattern.pipe
import de.woq.blended.modules.FilterComponent
import org.osgi.framework.BundleContext

import scala.concurrent.duration._
import scala.concurrent.Future

trait OSGIActor extends Actor with ActorLogging { this: BundleName =>

  implicit val timeout = new Timeout(1.second)
  implicit val ec = context.dispatcher

  def bundleActor(bundleName : String) = {
    log debug s"Trying to resolve bundle actor [$bundleName]"
    context.actorSelection(s"/user/$bundleName").resolveOne()
  }

  def osgiFacade = bundleActor(BlendedAkkaConstants.osgiFacadePath)

  def createTracker[I <: AnyRef](clazz : Class[I], filter: Option[FilterComponent] = None) = for {
    facade <- osgiFacade.mapTo[ActorRef]
    tracker <- (facade ? CreateTracker(clazz, self, filter)).mapTo[ActorRef]
  } yield tracker

  def getActorConfig(id: String) = for {
      facade <- osgiFacade.mapTo[ActorRef]
      config <- (facade ? ConfigLocatorRequest(id)).mapTo[ConfigLocatorResponse]
    } yield config

  def getServiceRef[I <: AnyRef](clazz : Class[I]) = {
    for {
      facade <- osgiFacade.mapTo[ActorRef]
      service <- (facade ? GetService(clazz)).mapTo[Service]
    } yield service
  }

  def invokeService[I <: AnyRef, T <: AnyRef](iface: Class[I])(f: InvocationType[I,T]) : Future[ServiceResult[Option[T]]] = {

    for {
      s <- getServiceRef[I](iface).mapTo[Service]
      r <- (s.service ? InvokeService(f)).mapTo[ServiceResult[Option[T]]]
    } yield (s,r) match {
      case (svc, result) => {
        svc.service ! UngetServiceReference
        result
      }
    }
  }
}

trait InitializingActor extends OSGIActor { this: BundleName =>

  case object Initialized

  def initialize(config: Config)(implicit bundleContext: BundleContext) : Unit

  def working : Receive

  def initializing : Receive = {
    case InitializeBundle(context) => {
      log.debug(s"Initializing bundle actor [${bundleSymbolicName}]")
      getActorConfig(bundleSymbolicName).mapTo[ConfigLocatorResponse].map { response =>
        self ! (context, response)
      }
    }
    case (bc: BundleContext, ConfigLocatorResponse(id, cfg)) if id == bundleSymbolicName => {
      initialize(cfg)(bc)
    }
    case Initialized => {
      log.debug(s"Initialized bundle actor [${bundleSymbolicName}]")
      context.system.eventStream.publish(BundleActorInitialized(bundleSymbolicName))
      context.become(working)
    }
  }
}
