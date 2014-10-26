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

import akka.actor.{Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import org.osgi.framework.BundleContext

import de.woq.blended.akka.protocol._

object OSGIServiceTrackers {
  def apply(osgiContext : BundleContext) =
    new OSGIServiceTrackers with BundleContextProvider {
      override implicit val bundleContext = osgiContext
    }
}
class OSGIServiceTrackers extends Actor with ActorLogging { this: BundleContextProvider =>

  def receive = LoggingReceive {
    case ct : CreateTracker[_] =>
      log.debug(s"Creating tracker for observer [${ct.observer}]")
      val tracker = context.actorOf(Props(OSGIServiceTracker(ct.clazz, ct.observer, ct.filter)))
      sender ! Tracker(tracker)
  }
}
