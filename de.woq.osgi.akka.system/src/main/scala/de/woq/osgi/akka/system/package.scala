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

package de.woq.osgi.akka

import org.osgi.framework.BundleContext
import scala.concurrent.Future
import akka.actor.{ActorSystem, ActorRef}
import akka.util.Timeout
import scala.concurrent.duration._
import de.woq.osgi.akka.modules._

package object system {

  implicit def context2Facade(bundleContext: BundleContext) : Future[ActorRef] = {

    implicit val bc = bundleContext
    implicit val timeOut = Timeout(1.second)

    (bundleContext findService(classOf[ActorSystem]) match {
      case Some(ref) => ref invokeService { system =>
        system.actorSelection(s"/user/${WOQAkkaConstants.osgiFacadePath}").resolveOne()
      }
      case _ => throw new IllegalStateException("No Actor System found as OSGI Service.")
    }) match {
      case Some(facade) => facade
      case _ => throw new IllegalStateException("No Actor System found.")
    }
  }

}
