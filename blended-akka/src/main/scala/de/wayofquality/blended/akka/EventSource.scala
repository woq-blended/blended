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

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import de.wayofquality.blended.akka.protocol._

import scala.concurrent.Future

trait EventSource {
  def sendEvent[T](event : T) : Unit
  def eventSourceReceive : Receive
}

trait ProductionEventSource extends EventSource{ this : Actor with ActorLogging =>

  var listeners = Vector.empty[ActorRef]

  def sendEvent[T](event : T) : Unit = {
    listeners foreach { _ ! event }
  }

  def eventSourceReceive = {
    case RegisterListener(l) =>
      context.watch(l)
      listeners = listeners :+ l
    case DeregisterListener(l) =>
      listeners = listeners filter { _ != l }
    case SendEvent(event) => sendEvent(event)
    case Terminated(l) => self ! DeregisterListener(l)
  }
}

trait OSGIEventSourceListener extends OSGIActor { this : BundleName =>

  var publisher = context.system.deadLetters

  def setupListener(publisherBundleName : String) : Future[ActorRef] = {
    context.system.eventStream.subscribe(self, classOf[BundleActorInitialized])

    bundleActor(publisherBundleName).map { actor : ActorRef => 
      if (actor != context.system.deadLetters) {
        actor ! RegisterListener(self)
        context.watch(actor)
      }
      publisher = actor
      publisher
    }
  }

  def cleanUp() : Unit = {
    context.system.eventStream.unsubscribe(self)
  }

  def eventListenerReceive(publisherBundleName: String) : Receive = {
    case Terminated(p) if p == publisher =>
      context.unwatch(p)
      publisher = context.system.deadLetters
    case BundleActorInitialized(name) if name == publisherBundleName => setupListener(publisherBundleName)
  }
}