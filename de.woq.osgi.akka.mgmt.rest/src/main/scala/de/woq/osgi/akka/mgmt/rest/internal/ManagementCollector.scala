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
import spray.http.MediaTypes._
import de.woq.osgi.akka.system._
import de.woq.osgi.akka.system.ConfigLocatorResponse
import de.woq.osgi.akka.system.ConfigLocatorRequest
import de.woq.osgi.akka.system.InitializeBundle
import akka.actor.{Actor, ActorLogging}
import org.osgi.framework.BundleContext
import akka.pattern._

object ManagementCollector {
  def apply()(implicit bundleContext: BundleContext) = new ManagementCollector with OSGIActor with CollectorBundleName
}

class ManagementCollector extends CollectorService with Actor with ActorLogging { this : OSGIActor with BundleName =>

  def actorRefFactory = context

  def initializing : Receive = {
    case InitializeBundle(bundleContext) => {
      osgiFacade map { ref => ref ? ConfigLocatorRequest(bundleSymbolicName) } pipeTo(self)
    }
    case ConfigLocatorResponse(bundleId, config) => {
      context.become(runRoute(collectorRoute))
    }
  }

  override def receive = initializing
}

trait CollectorService extends HttpService {

  val collectorRoute = path("/") {
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