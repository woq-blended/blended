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

import akka.actor.{Terminated, ActorRef, ActorLogging, Actor}
import de.woq.blended.modules.Filter
import de.woq.blended.modules.FilterComponent._
import org.osgi.framework.{ServiceReference, BundleContext}
import org.osgi.util.tracker.{ServiceTracker, ServiceTrackerCustomizer}
import akka.event.{LoggingAdapter, LoggingReceive}
import de.woq.blended.modules._

import de.woq.blended.akka.protocol._

trait TrackerAdapterProvider[I <: AnyRef] {
  def trackerAdapter(observer: ActorRef)(implicit osgiContext : BundleContext) : TrackerAdapter[I]
}

trait TrackerAdapter[I <: AnyRef] extends ServiceTrackerCustomizer[I, I] {

  val log : LoggingAdapter
  val trackerObserver : ActorRef

  override def modifiedService(svcRef: ServiceReference[I], svc: I) : Unit = {
    val msg = TrackerModifiedService(svcRef, svc)
    log.debug(s"Notifying [${trackerObserver}] with [${msg}]")
    trackerObserver ! msg
  }

  override def removedService(svcRef: ServiceReference[I], svc: I) : Unit = {
    val msg = TrackerRemovedService(svcRef, svc)
    log.debug(s"Notifying [${trackerObserver}] with [${msg}]")
    trackerObserver ! msg
  }

  override def addingService(svcRef: ServiceReference[I]) = {
    val svc = svcRef.getBundle.getBundleContext.getService(svcRef)
    val msg = TrackerAddingService(svcRef, svc)
    log.debug(s"Notifying [${trackerObserver}] with [${msg}]")
    trackerObserver ! msg
    svc
  }
}

object OSGIServiceTracker {

  def apply[I <: AnyRef] (
    clazz: Class[I],
    observer: ActorRef,
    filter: Option[FilterComponent] = None
  )(implicit osgiContext: BundleContext) : OSGIServiceTracker[I] = {
    new OSGIServiceTracker(clazz, observer, filter) with BundleContextProvider with TrackerAdapterProvider[I] {
      override implicit val bundleContext: BundleContext = osgiContext
      override def trackerAdapter(observer: ActorRef)(implicit osgiContext: BundleContext) =
        new TrackerAdapter[I] {
          override val trackerObserver: ActorRef = observer
          override val log: LoggingAdapter = context.system.log
        }
    }
  }
}

class OSGIServiceTracker[I <: AnyRef](clazz : Class[I], observer: ActorRef, filter: Option[FilterComponent] = None)
  extends Actor with ActorLogging {
  this : BundleContextProvider with TrackerAdapterProvider[I] =>

  case object Initialize

  def initializing : Receive = LoggingReceive {
    case Initialize => {
      val tracker = filter match {
        case None =>
          log.info(s"Creating Service tracker for class [${clazz}] for [${observer}]")
          new ServiceTracker[I, I](
            bundleContext, clazz, trackerAdapter(observer)
          )
        case Some(f) =>
          val realFilter : Filter = ("objectClass" === clazz.getName) && f
          log.info(s"Creating Service tracker with filter [${realFilter}] for [${observer}]")
          new ServiceTracker[I, I](
            bundleContext, bundleContext.createFilter(realFilter.toString), trackerAdapter(observer)
          )
      }

      context.watch(observer)
      tracker.open()
      log info s"Initialized Service Tracker for [${clazz.getName}]."
      context.become(tracking(tracker, observer))
    }
    case TrackerClose => context.stop(self)
  }

  def tracking(tracker: ServiceTracker[I, I], observer: ActorRef) : Receive = {
    case TrackerClose => stopTracker(tracker)
    case Terminated(actor) if actor == observer => stopTracker(tracker)
  }

  def receive = initializing

  override def preStart() : Unit = {
    self ! Initialize
  }

  private def stopTracker(tracker: ServiceTracker[I, I]) : Unit = {
    tracker.close()
    context.stop(self)
  }

}
