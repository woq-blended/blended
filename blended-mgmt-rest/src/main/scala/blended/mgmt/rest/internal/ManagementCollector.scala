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
import akka.actor.{ Actor, ActorRefFactory }
import akka.util.Timeout
import blended.akka.{ OSGIActor, OSGIActorConfig, ProductionEventSource }
import blended.mgmt.base.json._
import blended.spray.{ SprayOSGIBridge, SprayOSGIServlet }
import spray.http.Uri.Path
import spray.httpx.SprayJsonSupport
import spray.routing._
import spray.servlet.ConnectorSettings
import spray.util.LoggingContext
import scala.concurrent.duration._
import akka.actor.Props
import com.typesafe.config.Config
import blended.mgmt.base.ContainerInfo
import blended.mgmt.base.ContainerRegistryResponseOK
import blended.mgmt.base.UpdateContainerInfo
import blended.mgmt.base.UpdateAction
import org.slf4j.LoggerFactory
import blended.updater.remote.RemoteUpdater

trait CollectorService extends HttpService { this: SprayJsonSupport =>

  private[this] val log = LoggerFactory.getLogger(classOf[CollectorService])

  def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK

  val collectorRoute = {

    implicit val timeout = Timeout(1.second)

    path("container") {
      post {
        handleWith { info: ContainerInfo =>
          log.debug("Processing container info: {}", info)
          val res = processContainerInfo(info)
          log.debug("Processing result: {}", res)
          res
        }
      }
    }
  }
}

case class ManagementCollectorConfig(
    servletSettings: ConnectorSettings,
    routingSettings: RoutingSettings,
    contextPath: String,
    remoteUpdater: RemoteUpdater) {

  override def toString(): String = s"${getClass.getSimpleName}(servletSettings=${servletSettings},routingSettings=${routingSettings},contextPath=${contextPath},remoteUpdater=${remoteUpdater})"
}

object ManagementCollectorConfig {
  def apply(config: Config, contextPath: String, remoteUpdater: RemoteUpdater): ManagementCollectorConfig = ManagementCollectorConfig(
    servletSettings = ConnectorSettings(config).copy(rootPath = Path(s"/${contextPath}")),
    routingSettings = RoutingSettings(config),
    contextPath = contextPath,
    remoteUpdater = remoteUpdater
  )
}

object ManagementCollector {

  def props(cfg: OSGIActorConfig, config: ManagementCollectorConfig): Props = Props(new ManagementCollector(cfg, config))
}

class ManagementCollector(cfg: OSGIActorConfig, config: ManagementCollectorConfig)
    extends OSGIActor(cfg)
    with CollectorService
    with ProductionEventSource
    with SprayJsonSupport {

  type ContainerId = String

  private[this] val mylog = LoggerFactory.getLogger(classOf[ManagementCollector])
  
  // mutable vars
  private[this] var cachedActions: Map[ContainerId, Seq[UpdateAction]] = Map()

  // Required by CollectorService
  override implicit def actorRefFactory: ActorRefFactory = context

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    mylog.debug("Processing container info: {}", info)
    sendEvent(UpdateContainerInfo(info))
    val updater = config.remoteUpdater
    updater.removeResolvedActions(info)
    val newActions = updater.getContainerActions(info.containerId)
    ContainerRegistryResponseOK(info.containerId, newActions)
  }

  override def preStart(): Unit = {
    val servletSettings = config.servletSettings
    implicit val routingSettings = config.routingSettings
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

    servlet.providesService[Servlet](
      "urlPatterns" -> "/",
      "Webapp-Context" -> config.contextPath,
      "Web-ContextPath" -> s"/${config.contextPath}"
    )

    context.become(runRoute(collectorRoute))
  }

  def receive: Receive = Actor.emptyBehavior

}
