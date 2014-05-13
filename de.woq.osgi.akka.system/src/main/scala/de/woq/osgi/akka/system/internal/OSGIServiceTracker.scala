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

import akka.actor.{ActorRef, ActorLogging, Actor}
import org.osgi.framework.{ServiceReference, BundleContext}
import org.osgi.util.tracker.{ServiceTracker, ServiceTrackerCustomizer}
import akka.event.LoggingReceive
import de.woq.osgi.akka.system.protocol._

trait TrackerAdapterProvider[I <: AnyRef] {
  def trackerAdapter(observer: ActorRef)(implicit osgiContext : BundleContext) : TrackerAdapter[I]
}

trait TrackerAdapter[I <: AnyRef] extends ServiceTrackerCustomizer[I, I] {

  val trackerObserver : ActorRef

  override def modifiedService(svcRef: ServiceReference[I], svc: I) {
    trackerObserver ! TrackerModifiedService(svcRef, svc)
  }

  override def removedService(svcRef: ServiceReference[I], svc: I) {
    trackerObserver ! TrackerRemovedService(svcRef, svc)
  }

  override def addingService(svcRef: ServiceReference[I]) = {
    val svc = svcRef.getBundle.getBundleContext.getService(svcRef)
    trackerObserver ! TrackerAddingService(svcRef, svc)
    svc
  }
}

object OSGIServiceTracker {
  def apply[I <: AnyRef](
    clazz : Class[I],
    observer: ActorRef
  )(implicit osgiContext : BundleContext) : OSGIServiceTracker[I] = {
    apply[I](clazz, observer, new TrackerAdapter[I] with BundleContextProvider {
      override implicit val bundleContext: BundleContext = osgiContext
      override val trackerObserver: ActorRef = observer
    })
  }

  def apply[I <: AnyRef] (
    clazz: Class[I],
    observer: ActorRef,
    adapter: TrackerAdapter[I]
  )(implicit osgiContext: BundleContext) : OSGIServiceTracker[I] = {
    new OSGIServiceTracker(clazz, observer) with BundleContextProvider with TrackerAdapterProvider[I] {
      override implicit val bundleContext: BundleContext = osgiContext
      override def trackerAdapter(observer: ActorRef)(implicit osgiContext: BundleContext) = adapter
    }
  }
}

class OSGIServiceTracker[I <: AnyRef](clazz : Class[I], observer: ActorRef) extends Actor with ActorLogging {
  this : BundleContextProvider with TrackerAdapterProvider[I] =>

  case object Initialize

  def initializing : Receive = LoggingReceive {
    case Initialize => {
      val tracker = new ServiceTracker[I, I](
        bundleContext, clazz, trackerAdapter(observer)
      )
      tracker.open()
      log info s"Initialized Service Tracker for [${clazz.getName}]."
      context.become(tracking(tracker))
    }
    case TrackerClose => context.stop(self)
  }

  def tracking(tracker: ServiceTracker[I, I]) : Receive = {
    case TrackerClose => {
      tracker.close()
      context.stop(self)
    }
  }

  def receive = initializing

  override def preStart() {
    self ! Initialize
  }

}
