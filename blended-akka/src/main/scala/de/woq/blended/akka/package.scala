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

package de.woq.blended

import scala.language.implicitConversions
import _root_.akka.actor.{ActorSystem, ActorRef}
import _root_.akka.event.LoggingAdapter
import _root_.akka.util.Timeout
import de.woq.blended.modules._
import org.osgi.framework.BundleContext

import scala.concurrent.Future
import scala.concurrent.duration._

package object akka {

  implicit def context2Facade(bundleContext: BundleContext)(implicit log: LoggingAdapter) : Future[ActorRef] = {

    implicit val bc = bundleContext
    implicit val timeOut = Timeout(1.second)

    (bundleContext findService(classOf[ActorSystem]) match {
      case Some(ref) => ref invokeService { system =>
        system.actorSelection(s"/user/${BlendedAkkaConstants.osgiFacadePath}").resolveOne()
      }
      case _ => throw new IllegalStateException("No Actor System found as OSGI Service.")
    }) match {
      case Some(facade) => facade
      case _ => throw new IllegalStateException("No Actor System found.")
    }
  }

}
