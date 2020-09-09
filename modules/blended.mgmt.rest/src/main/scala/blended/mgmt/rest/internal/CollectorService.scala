package blended.mgmt.rest.internal

import scala.collection.{immutable => sci}

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import blended.prickle.akka.http.PrickleSupport
import blended.security.akka.http.BlendedSecurityDirectives
import blended.updater.config._
import blended.updater.config.json.PrickleProtocol._
import blended.util.logging.Logger

trait CollectorService {
  // dependencies
  deps: BlendedSecurityDirectives with PrickleSupport =>

  val httpRoute: Route =
    respondWithDefaultHeader(headers.`Access-Control-Allow-Origin`(headers.HttpOriginRange.*)) {
      collectorRoute ~
        infoRoute ~
        versionRoute
    }

  private[this] lazy val log = Logger[CollectorService]

  def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK

  def getCurrentState(): sci.Seq[RemoteContainerState]

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
