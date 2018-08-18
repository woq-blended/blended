package blended.mgmt.rest.internal

import scala.collection.immutable
import scala.concurrent.duration._

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.ValidationRejection
import blended.security.BlendedPermissionManager
import akka.util.Timeout
import blended.prickle.akka.http.PrickleSupport
import blended.security.akka.http.BlendedSecurityDirectives
import blended.updater.config._
import blended.updater.config.json.PrickleProtocol._
import blended.util.logging.Logger

trait CollectorService {
  // dependencies
  deps: BlendedSecurityDirectives with PrickleSupport =>

  val mgr : BlendedPermissionManager

  val httpRoute: Route =
    respondWithDefaultHeader(headers.`Access-Control-Allow-Origin`(headers.HttpOriginRange.*)) {
      collectorRoute ~
        infoRoute ~
        versionRoute ~
        runtimeConfigRoute ~
        overlayConfigRoute ~
        updateActionRoute ~
        rolloutProfileRoute
    }

  private[this] lazy val log = Logger[CollectorService]

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

  def findMissingOverlayRef(configs: immutable.Seq[OverlayRef]): Option[OverlayRef] =
    if (configs.isEmpty) None
    else {
      val ocs = getOverlayConfigs()
      configs.find(c => !ocs.exists(oc => oc.name == c.name && oc.version == c.name))
    }

  def versionRoute: Route = {
    path("version") {
      get {
        complete {
          version
        }
      }
    }
  }

  def jsonReponse = pass // no longer supported

  def collectorRoute: Route = {

    implicit val timeout = Timeout(1.second)

    path("container") {
      post {
        entity(as[ContainerInfo]) { info =>
          log.debug(s"Processing container info: ${info}")
          val res = processContainerInfo(info)
          log.debug(s"Processing result: ${res}")
          complete(res)
        }
      }
    }
  }

  def infoRoute: Route = {
    path("container") {
      get {
        jsonReponse {
          complete {
            log.debug("About to provide container infos")
            val res = getCurrentState()
            log.debug(s"Result: ${res}")
            res
          }
        }
      }
    }
  }

  def runtimeConfigRoute: Route = {
    path("runtimeConfig") {
      get {
        jsonReponse {
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
        jsonReponse {
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

  def rolloutProfileRoute: Route = {
    path("rollout" / "profile") {
      post {
        requirePermission("profile:update") {
          entity(as[RolloutProfile]) { rolloutProfile =>
            // check existence of profile
            getRuntimeConfigs().find(rc => rc.name == rolloutProfile.profileName && rc.version == rolloutProfile.profileVersion) match {
              case None =>
                reject(ValidationRejection(s"Unknown profile ${rolloutProfile.profileName} ${rolloutProfile.profileVersion}"))
              case _ =>
                // check existence of overlays
                findMissingOverlayRef(rolloutProfile.overlays) match {
                  case Some(r) =>
                    reject(ValidationRejection(s"Unknown overlay ${r.name} ${r.version}"))
                  case None =>
                    // all ok, complete
                    complete {
                      log.debug("looks good, rollout can continue")
                      rolloutProfile.containerIds.foreach { containerId =>
                        addUpdateAction(
                          containerId = containerId,
                          updateAction = StageProfile(
                            profileName = rolloutProfile.profileName,
                            profileVersion = rolloutProfile.profileVersion,
                            overlays = rolloutProfile.overlays
                          )
                        )
                      }
                      s"Recorded ${rolloutProfile.containerIds.size} rollout actions"
                    }
                }
            }
          }
        }
      }
    }

  }
}
