package blended.mgmt.rest.internal

import akka.util.Timeout
import blended.mgmt.base.json.JsonProtocol._
import blended.updater.config._
import org.slf4j.LoggerFactory
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport
import spray.routing.{HttpService, Route}

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
