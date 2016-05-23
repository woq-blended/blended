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

import akka.actor.Actor
import akka.actor.ActorRefFactory
import akka.actor.Props
import akka.util.Timeout
import blended.akka.OSGIActor
import blended.akka.OSGIActorConfig
import blended.akka.ProductionEventSource
import blended.mgmt.base.ContainerInfo
import blended.mgmt.base.ContainerRegistryResponseOK
import blended.mgmt.base.RemoteContainerState
import blended.mgmt.base.UpdateContainerInfo
import blended.mgmt.base.json._
import blended.spray.SprayOSGIBridge
import blended.spray.SprayOSGIServlet
import blended.updater.config.OverlayConfig
import blended.updater.config.RuntimeConfig
import blended.updater.remote.RemoteUpdater
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spray.http.HttpResponse
import spray.http.MediaTypes
import spray.http.StatusCodes
import spray.http.Uri.Path
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.ToResponseMarshallable
import spray.httpx.marshalling.ToResponseMarshaller
import spray.httpx.unmarshalling.FromRequestUnmarshaller
import spray.routing._
import spray.servlet.ConnectorSettings
import spray.util.LoggingContext

import scala.collection.immutable
import scala.collection.immutable
import scala.collection.immutable
import scala.concurrent.duration._

trait CollectorService extends HttpService {
  this: SprayJsonSupport =>

  private[this] val log = LoggerFactory.getLogger(classOf[CollectorService])

  def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK

  def getCurrentState(): immutable.Seq[RemoteContainerState]

  def registerRuntimeConfig(rc: RuntimeConfig): Unit

  def registerOverlayConfig(oc: OverlayConfig): Unit

  def getRuntimeConfigs(): immutable.Seq[RuntimeConfig]

  def getOverlayConfigs(): immutable.Seq[OverlayConfig]


  def version: String

  def versionRoute: Route = {
    path("version") {
      get {
        complete {
          version
        }
      }
    }
  }

  def collectorRoute: Route = {

    implicit val timeout = Timeout(1.second)

    path("container") {
      post {
        entity(as[ContainerInfo]) { info =>
          log.debug("Processing container info: {}", info)
          val res = processContainerInfo(info)
          log.debug("Processing result: {}", res)
          complete(res)
        }
      }
    }
  }

  def infoRoute: Route = {
    path("container") {
      get {
        respondWithMediaType(MediaTypes.`application/json`) {
          complete {
            log.debug("About to provide container infos")
            val res = getCurrentState()
            log.debug("Result: {}", res)
            res
          }
        }
      }
    }
  }

  def runtimeConfigRoute: Route = {
    path("runtimeConfig") {
      get {
        respondWithMediaType(MediaTypes.`application/json`) {
          complete {
            getRuntimeConfigs()
          }
        }
      } ~
        post {
          entity(as[RuntimeConfig]) { rc =>
            registerRuntimeConfig(rc)
            complete(s"Registered ${rc.name}-${rc.version}")
          }
        }
    }
  }

  def overlayConfigRoute: Route = {
    path("overlayConfig") {
      get {
        respondWithMediaType(MediaTypes.`application/json`) {
          complete {
            getOverlayConfigs()
          }
        }
      } ~
        post {
          entity(as[OverlayConfig]) { oc =>
            registerOverlayConfig(oc)
            complete(s"Registered ${oc.name}-${oc.version}")
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

  private[this] var containerStates: Map[ContainerId, RemoteContainerState] = Map()

  // Required by CollectorService
  override implicit def actorRefFactory: ActorRefFactory = context

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    mylog.debug("Processing container info: {}", info)
    sendEvent(UpdateContainerInfo(info))
    val updater = config.remoteUpdater
    val newActions = updater.getContainerActions(info.containerId)
    containerStates += info.containerId -> RemoteContainerState(info, newActions)
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

    context.become(runRoute(collectorRoute ~ infoRoute ~ versionRoute ~ runtimeConfigRoute ~ overlayConfigRoute))
  }

  def receive: Receive = Actor.emptyBehavior

  override def getCurrentState(): immutable.Seq[RemoteContainerState] = {
    mylog.debug("About to send state: {}", containerStates)
    containerStates.values.toList
  }

  override def version: String = cfg.bundleContext.getBundle().getVersion().toString()

  override def registerRuntimeConfig(rc: RuntimeConfig): Unit = config.remoteUpdater.registerRuntimeConfig(rc)

  override def registerOverlayConfig(oc: OverlayConfig): Unit = config.remoteUpdater.registerOverlayConfig(oc)

  override def getRuntimeConfigs(): immutable.Seq[RuntimeConfig] = config.remoteUpdater.getRuntimeConfigs()

  override def getOverlayConfigs(): immutable.Seq[OverlayConfig] = config.remoteUpdater.getOverlayConfigs()
}
