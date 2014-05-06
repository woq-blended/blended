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

import spray.routing._
import spray.http.MediaTypes._
import de.woq.osgi.akka.system._
import akka.actor._
import org.osgi.framework.BundleContext
import akka.pattern._
import de.woq.osgi.spray.servlet.OSGiConfigHolder
import spray.servlet.{WebBoot, ConnectorSettings}
import akka.event.LoggingReceive
import spray.util.LoggingContext
import de.woq.osgi.akka.system.ConfigLocatorResponse
import de.woq.osgi.akka.system.InitializeBundle
import javax.servlet.ServletContext

trait CollectorService extends HttpService {

  val collectorRoute =
    path("hello") {
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

class ManagementCollectorBoot(servletContext : ServletContext) extends WebBoot {
  override def serviceActor = OSGiConfigHolder.actorRef
  override def system = OSGiConfigHolder.actorSystem
}

object ManagementCollector {
  def apply()(implicit bundleContext: BundleContext) = new ManagementCollector() with OSGIActor with CollectorBundleName
}

class ManagementCollector()(implicit bundleContext : BundleContext)
  extends CollectorService
  with Actor
  with ActorLogging { this : OSGIActor with BundleName =>

  override implicit def actorRefFactory = context

  def receive = initializing

  def initializing = LoggingReceive {
    case InitializeBundle(_) => getActorConfig(bundleSymbolicName) pipeTo (self)

    case ConfigLocatorResponse(id, cfg) if id == bundleSymbolicName => {

      implicit val servletSettings = ConnectorSettings(cfg)
      OSGiConfigHolder.setConnectorSettings(ConnectorSettings(cfg))

      implicit val routingSettings = RoutingSettings(cfg)
      implicit val routeLogger = LoggingContext.fromAdapter(log)
      implicit val exceptionHandler = ExceptionHandler.default
      implicit val rejectionHandler = RejectionHandler.Default

      context.become(LoggingReceive {
        runRoute(collectorRoute)
      })
    }
  }
}

