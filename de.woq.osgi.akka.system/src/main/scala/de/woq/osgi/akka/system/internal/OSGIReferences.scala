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

package de.woq.osgi.akka.system.internal

import akka.actor._
import org.osgi.framework.{ServiceReference, BundleContext}
import de.woq.osgi.akka.modules._
import akka.event.LoggingReceive
import akka.actor.SupervisorStrategy.Stop
import de.woq.osgi.akka.system.OSGIProtocol
import scala.Some
import akka.actor.OneForOneStrategy
import scala.concurrent.duration._
import de.woq.osgi.akka.system.OSGIProtocol._

object OSGIReferences {

  def apply[I <: AnyRef]()(implicit osgiContext : BundleContext) = new OSGIReferences with BundleContextProvider {
    override val bundleContext = osgiContext
  }
}

object OfflineServiceTracker {

  def apply[I <: AnyRef](references : ActorRef)(implicit osgiContext : BundleContext) =
    new OfflineServiceTracker[I](references)

  case class ReferenceAdded[I <: AnyRef](referenceFor: ActorRef, svcRef: ServiceReference[I])
}

class OfflineServiceTracker[I <: AnyRef](references: ActorRef)(implicit osgiContext : BundleContext) extends Actor with ActorLogging {

  def initializing = LoggingReceive {
    case OSGIProtocol.CreateReference(clazz) if clazz.isInstanceOf[Class[I]] => {

      val requestor = sender
      implicit val executionContext = context.dispatcher
      val tracker = context.actorOf(Props(OSGIServiceTracker[I](clazz.asInstanceOf[Class[I]], self)))

      context.watch(tracker)

      val timer = context.system.scheduler.scheduleOnce(1.second, self, "timeout")
      context.become(waiting(requestor, tracker, timer))
    }
  }

  def waiting(requestor: ActorRef, tracker: ActorRef, timer: Cancellable) = LoggingReceive {
    case "timeout" => {
      requestor ! OSGIProtocol.Service(context.system.deadLetters)
      tracker ! TrackerClose
    }
    case TrackerAddingService(svcRef, svc) => {
      log info s"Sending to ${references.toString()}"
      references ! OfflineServiceTracker.ReferenceAdded(requestor, svcRef)
      timer.cancel()
      tracker ! TrackerClose
    }
    case Terminated(tracker) => context.stop(self)
  }

  def receive = initializing
}

class OSGIReferences extends Actor with ActorLogging { this : BundleContextProvider =>

  override def supervisorStrategy = OneForOneStrategy() {
    case _ => Stop
  }

  override def receive = LoggingReceive {
    case OSGIProtocol.CreateReference(clazz) => {
      bundleContext findService(clazz) match {
        case Some(ref) => {
          log info s"Creating Service reference actor..."
          sender ! OSGIProtocol.Service(context.actorOf(Props(OSGIServiceReference(ref))))
        }
        case None => {
          log info "Service Reference not available, Creating a Tracker..."
          context.actorOf(Props(OfflineServiceTracker(self))) forward CreateReference(clazz)
        }
      }
    }
    case OfflineServiceTracker.ReferenceAdded(referenceFor, svcRef) => {
      referenceFor ! OSGIProtocol.Service(context.actorOf(Props(OSGIServiceReference(svcRef))))
    }
  }
}
