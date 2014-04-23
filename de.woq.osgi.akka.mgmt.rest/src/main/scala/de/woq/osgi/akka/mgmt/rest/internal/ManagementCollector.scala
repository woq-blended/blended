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

package de.woq.osgi.akka.mgmt.rest.internal

import spray.routing.HttpService
import akka.actor.Actor
import spray.http.MediaTypes._
import de.woq.osgi.akka.osgi.{BundleName, ConfigLocatorResponse, ConfigLocatorRequest, InitializeBundle}
import de.woq.osgi.akka.osgi.WOQAkkaConstants._
import akka.util.Timeout
import scala.concurrent.duration._

object ManagementCollector {
  def apply() = new ManagementCollector with BundleName {
    override def bundleSymbolicName = CollectorBundleName.bundleName
  }
}

class ManagementCollector extends Actor with CollectorService { this : BundleName =>

  implicit val executionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)
  def actorRefFactory = context

  def initializing : Receive = {
    case InitializeBundle(bundleContext) => {
      context.system.actorSelection(s"/user/$configLocatorPath").resolveOne() map {
        ref => ref ! ConfigLocatorRequest(bundleSymbolicName)
      }
    }
    case ConfigLocatorResponse(bundleId, config) => {
      context.become(runRoute(collectorRoute))
    }
  }

  override def receive: Actor.Receive = initializing
}

trait CollectorService extends HttpService {

  val collectorRoute = path("") {
    get {
      respondWithMediaType(`text/html`) {
        complete {
          <html>
            <body>Say hello to <i>spray routing</i> within OSGi.</body>
          </html>
        }
      }
    }
  }
}