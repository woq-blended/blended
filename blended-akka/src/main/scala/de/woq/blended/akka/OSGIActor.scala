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
import akka.pattern.{pipe,ask}
import akka.util.Timeout
import com.typesafe.config.Config
import de.woq.blended.akka.protocol._
import de.woq.blended.modules.FilterComponent
import org.osgi.framework.BundleContext

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success, Try}

trait OSGIActor extends Actor with ActorLogging { this: BundleName =>

  implicit val timeout = new Timeout(500.millis)
  implicit val ec = context.dispatcher

  def bundleActor(bundleName : String) : Future[ActorRef] = {
    log debug s"Trying to resolve bundle actor [$bundleName]"
    context.actorSelection(s"/user/$bundleName").resolveOne().fallbackTo(Future(context.system.deadLetters))
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
      case (svc, result) =>
        svc.service ! UngetServiceReference
        result
    }
  }
}

trait InitializingActor[T <: BundleActorState] extends OSGIActor { this: BundleName =>
  
  type StateCleanup = T => Unit
  
  case class Initialized(state: T)

  def initialize(state : T) : Future[Try[Initialized]] = Future(Success(Initialized(state)))
  
  def cleanup() : () => Unit = { () => {} }
  def working(state : T) : Receive
  
  def becomeWorking(state : T) : Unit = {
    context.become(working(state))
  }
  
  def createState(cfg: Config, bundleContext: BundleContext) : T

  def initializing[T](implicit tag : TypeTag[T]) : Receive = {
    
    case InitializeBundle(bc) =>
      log.info(s"Retrieving config for [$bundleSymbolicName]")
      getActorConfig(bundleSymbolicName).mapTo[ConfigLocatorResponse].map { response =>
        self ! (bc, response)
      }
    
    case (bc: BundleContext, ConfigLocatorResponse(id, cfg)) if id == bundleSymbolicName =>
      log.info(s"Initializing bundle actor [$bundleSymbolicName]")
      val state = createState(cfg, bc)
      
      initialize(state).mapTo[Try[Initialized]].pipeTo(self)
      
    case Success(initialized) =>
      log.debug(s"Bundle actor [$bundleSymbolicName] initialized")
      context.system.eventStream.publish(BundleActorInitialized(bundleSymbolicName))
      becomeWorking(initialized.asInstanceOf[Initialized].state)
      
    case Failure(e) => 
      log.error(s"Error initializing bundle actor [$bundleSymbolicName].", e)
      context.stop(self)
  }

  override def postStop(): Unit = {
    cleanup()
    super.postStop()
  }
}

