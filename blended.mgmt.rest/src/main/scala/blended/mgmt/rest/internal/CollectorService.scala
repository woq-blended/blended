package blended.mgmt.rest.internal

import akka.util.Timeout
import blended.spray.{ BlendedHttpRoute, SprayPrickleSupport }
import blended.updater.config._
import blended.updater.config.json.PrickleProtocol._
import org.slf4j.LoggerFactory
import spray.http.MediaTypes
import spray.routing.Route
import blended.security.spray.BlendedSecuredRoute

import scala.collection.immutable
import scala.concurrent.duration._

trait CollectorService
    extends BlendedHttpRoute
    with BlendedSecuredRoute {
  this: SprayPrickleSupport =>

  override val httpRoute: Route =
    collectorRoute ~
      infoRoute ~
      versionRoute ~
      runtimeConfigRoute ~
      overlayConfigRoute ~
      updateActionRoute

  private[this] val log = LoggerFactory.getLogger(classOf[CollectorService])

  def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK

  def getCurrentState(): immutable.Seq[RemoteContainerState]

  /** Register a runtime config into the management container. */
  def registerRuntimeConfig(rc: RuntimeConfig): Unit

  /** Register a overlay config into the management container. */
  def registerOverlayConfig(oc: OverlayConfig): Unit

  /** Get all registered runtime configs of the management container. */
  def getRuntimeConfigs(): immutable.Seq[RuntimeConfig]

  /** Get all registered overlay configs of the managament container. */
  def getOverlayConfigs(): immutable.Seq[OverlayConfig]

  /** Promote (stage) an update action to a container. */
  def addUpdateAction(containerId: String, updateAction: UpdateAction): Unit

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
          requirePermission("profile:update") {
            entity(as[RuntimeConfig]) { rc =>
              registerRuntimeConfig(rc)
              complete(s"Registered ${rc.name}-${rc.version}")
            }
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
          requirePermission("profile:update") {
            entity(as[OverlayConfig]) { oc =>
              registerOverlayConfig(oc)
              complete(s"Registered ${oc.name}-${oc.version}")
            }
          }
        }
    }
  }

  def updateActionRoute: Route = {
    path("container" / Segment / "update") { containerId =>
      post {
        requirePermission("profile:update") {
          entity(as[UpdateAction]) { updateAction =>
            addUpdateAction(containerId, updateAction)
            complete(s"Added UpdateAction to ${containerId}")
          }
        }
      }
    }
  }

}
