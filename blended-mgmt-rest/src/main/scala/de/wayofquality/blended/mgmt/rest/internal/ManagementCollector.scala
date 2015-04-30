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

package de.wayofquality.blended.mgmt.rest.internal

import akka.event.LoggingReceive
import akka.util.Timeout
import com.typesafe.config.Config
import de.wayofquality.blended.akka.protocol._
import de.wayofquality.blended.akka.{InitializingActor, OSGIActor, OSGIEventSource}
import de.wayofquality.blended.container.registry.protocol._
import de.wayofquality.blended.modules._
import de.wayofquality.blended.spray.{SprayOSGIBridge, SprayOSGIServlet}
import org.osgi.framework.BundleContext
import spray.http.Uri.Path
import spray.httpx.SprayJsonSupport
import spray.routing._
import spray.servlet.ConnectorSettings
import spray.util.LoggingContext

import scala.concurrent.duration._
import scala.util.{Success, Try}

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

  def apply(contextPath: String, bc: BundleContext) =
    new ManagementCollector(contextPath, bc) with SprayJsonSupport with OSGIEventSource
}

class ManagementCollector(contextPath: String, bc: BundleContext)
  extends CollectorService with InitializingActor[BundleActorState] with CollectorBundleName {
  this : OSGIActor with CollectorBundleName with SprayJsonSupport with OSGIEventSource =>
  
  override protected def bundleContext: BundleContext = bc

  override implicit def actorRefFactory = context

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    sendEvent(UpdateContainerInfo(info))
    ContainerRegistryResponseOK(info.containerId)
  }
  
  override def createState(cfg: Config, bundleContext: BundleContext): BundleActorState = 
    BundleActorState(cfg, bundleContext)

  def receive = LoggingReceive { initializing orElse eventSourceReceive }


  override def initialize(state: BundleActorState): Try[Initialized] = {
    implicit val servletSettings = ConnectorSettings(state.config).copy(rootPath = Path(s"/$contextPath"))
    implicit val routingSettings = RoutingSettings(state.config)
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
      )
    )
    Success(Initialized(state))
  }

  override def working(state: BundleActorState): Receive = 
    LoggingReceive { runRoute(collectorRoute) orElse eventSourceReceive}
}
