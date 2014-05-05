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

import spray.routing.{RoutingSettings, RejectionHandler, ExceptionHandler, HttpService}
import spray.http.MediaTypes._
import de.woq.osgi.akka.system._
import akka.actor._
import org.osgi.framework.BundleContext
import akka.pattern._
import de.woq.osgi.spray.servlet.{SprayOSGIBridge, SprayOSGIServlet}
import com.typesafe.config.Config
import javax.servlet.ServletContext
import spray.servlet.ConnectorSettings
import akka.event.LoggingReceive
import de.woq.osgi.akka.system.ConfigLocatorResponse
import de.woq.osgi.akka.system.InitializeBundle
import spray.util.LoggingContext

trait CollectorService extends HttpService {

  val collectorRoute = path("/hello") {
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

object ManagementCollector {
  def apply()(implicit bundleContext: BundleContext) = new ManagementCollector with OSGIActor with CollectorBundleName

  sealed trait State
  case object Starting extends State
  case object Initializing extends State
  case object Serving extends State

  sealed trait Data
  case object Uninitialized extends Data
  case class ConfigData(config: Option[Config], servletContext: Option[ServletContext]) extends Data
}

class ManagementCollector extends CollectorService with Actor with ActorLogging { this : OSGIActor with CollectorBundleName =>
  override implicit def actorRefFactory = context

  def receive = initializing

  def initializing = LoggingReceive {
    case InitializeBundle(_) => getActorConfig(bundleSymbolicName) pipeTo (self)
    case ConfigLocatorResponse(id, cfg) if id == bundleSymbolicName => {

      implicit val servletSettings = ConnectorSettings(cfg)
      implicit val routingSettings = RoutingSettings(cfg)
      implicit val routeLogger = LoggingContext.fromAdapter(log)
      implicit val exceptionHandler = ExceptionHandler.default
      implicit val rejectionHandler = RejectionHandler.Default

      val actorSys = context.system
      val routingActor = self

      val servlet = new SprayOSGIServlet with SprayOSGIBridge {
        override def routeActor = routingActor
        override def connectorSettings = servletSettings
        override def actorSystem = actorSys
      }

      invokeService[org.osgi.service.http.HttpService, String](classOf[org.osgi.service.http.HttpService]){ svc =>
        svc.registerServlet("/woq", servlet, null, null)
        ""
      } onSuccess {
        case _ => context.become(LoggingReceive {
          runRoute(collectorRoute)
        })
      }
    }
  }
}

