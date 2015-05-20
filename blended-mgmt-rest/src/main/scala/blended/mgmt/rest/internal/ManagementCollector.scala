/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.mgmt.rest.internal

import javax.servlet.Servlet

import akka.actor.{Actor, ActorRefFactory}
import akka.util.Timeout
import blended.akka.{OSGIActor, OSGIActorConfig, ProductionEventSource}
import blended.container.registry.protocol._
import blended.spray.{SprayOSGIBridge, SprayOSGIServlet}
import blended.container.registry.protocol.{ContainerRegistryResponseOK, UpdateContainerInfo, ContainerInfo}
import spray.http.Uri.Path
import spray.httpx.SprayJsonSupport
import spray.routing._
import spray.servlet.ConnectorSettings
import spray.util.LoggingContext

import scala.concurrent.duration._

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

  def apply(cfg: OSGIActorConfig, contextPath: String) =
    new ManagementCollector(cfg, contextPath)
}

class ManagementCollector(cfg: OSGIActorConfig, contextPath: String)
  extends OSGIActor(cfg)
  with CollectorService
  with ProductionEventSource
  with SprayJsonSupport {

  override implicit def actorRefFactory : ActorRefFactory = context

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    sendEvent(UpdateContainerInfo(info))
    ContainerRegistryResponseOK(info.containerId)
  }

  override def preStart(): Unit = {
    implicit val servletSettings = ConnectorSettings(cfg.config).copy(rootPath = Path(s"/$contextPath"))
    implicit val routingSettings = RoutingSettings(cfg.config)
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

    servlet.providesService[Servlet] ( Map(
      "urlPatterns" -> "/",
      "Webapp-Context" -> contextPath,
      "Web-ContextPath" -> s"/$contextPath"
    ))

    context.become(runRoute(collectorRoute))
  }


  def receive : Receive = Actor.emptyBehavior

}
