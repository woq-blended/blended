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

package de.woq.blended.mgmt.rest.internal

import de.woq.blended.akka.{ProductionEventSource, OSGIActor}
import de.woq.blended.spray.{SprayOSGIServlet, SprayOSGIBridge}
import spray.routing._
import akka.actor._
import org.osgi.framework.BundleContext
import akka.pattern._
import spray.servlet.ConnectorSettings
import akka.event.LoggingReceive
import spray.util.LoggingContext
import spray.http.Uri.Path
import scala.concurrent.duration._
import akka.util.Timeout
import spray.httpx.SprayJsonSupport

import de.woq.blended.container.registry.protocol._
import de.woq.blended.akka.protocol._
import de.woq.blended.modules._

trait CollectorService extends HttpService { this : SprayJsonSupport =>

  def processContainerInfo(info :ContainerInfo) : ContainerRegistryResponseOK

  val collectorRoute = {

    implicit val timeout = Timeout(1.second)

    path("container") {
      post {
        handleWith { info : ContainerInfo => { processContainerInfo(info) } }
      }
    }
  }
}

object ManagementCollector {

  def apply(contextPath: String)(implicit bundleContext: BundleContext) =
    new ManagementCollector(contextPath) with OSGIActor with CollectorBundleName with SprayJsonSupport with ProductionEventSource
}

class ManagementCollector(contextPath: String)(implicit bundleContext: BundleContext)
  extends CollectorService with Actor with ActorLogging {
  this : OSGIActor with CollectorBundleName with SprayJsonSupport with ProductionEventSource =>

  override implicit def actorRefFactory = context

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    sendEvent(UpdateContainerInfo(info))
    ContainerRegistryResponseOK(info.containerId)
  }

  def receive = LoggingReceive { initializing orElse eventSourceReceive }

  def initializing : Receive = {
    case InitializeBundle(_) => getActorConfig(bundleSymbolicName) pipeTo (self)
    case ConfigLocatorResponse(id, cfg) if id == bundleSymbolicName => {

      implicit val servletSettings = ConnectorSettings(cfg).copy(rootPath = Path(s"/$contextPath"))
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

      bundleContext.createService(
        servlet, Map(
          "urlPatterns" -> "/",
          "Webapp-Context" -> contextPath,
          "Web-ContextPath" -> s"/$contextPath"
        ))

      context.become(
        LoggingReceive { runRoute(collectorRoute) orElse eventSourceReceive}
      )
    }
  }
}
