package blended.mgmt.rest.internal

import java.io.File

import scala.collection.{ immutable => sci }
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.ValidationRejection
import akka.util.Timeout
import blended.prickle.akka.http.PrickleSupport
import blended.security.akka.http.BlendedSecurityDirectives
import blended.updater.config._
import blended.updater.config.json.PrickleProtocol._
import blended.updater.config.util.Unzipper
import blended.util.logging.Logger
import com.typesafe.config.ConfigFactory

trait CollectorService {
  // dependencies
  deps: BlendedSecurityDirectives with PrickleSupport =>

  val httpRoute: Route =
    respondWithDefaultHeader(headers.`Access-Control-Allow-Origin`(headers.HttpOriginRange.*)) {
      collectorRoute ~
        infoRoute ~
        versionRoute ~
        runtimeConfigRoute ~
        overlayConfigRoute ~
        updateActionRoute ~
        rolloutProfileRoute ~
        uploadDeploymentPackRoute
    }

  private[this] lazy val log = Logger[CollectorService]

  def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK

  def getCurrentState(): sci.Seq[RemoteContainerState]

  /** Register a runtime config into the management container. */
  def registerRuntimeConfig(rc: RuntimeConfig): Unit

  /** Register a overlay config into the management container. */
  def registerOverlayConfig(oc: OverlayConfig): Unit

  /** Get all registered runtime configs of the management container. */
  def getRuntimeConfigs(): sci.Seq[RuntimeConfig]

  /** Get all registered overlay configs of the managament container. */
  def getOverlayConfigs(): sci.Seq[OverlayConfig]

  /** Promote (stage) an update action to a container. */
  def addUpdateAction(containerId: String, updateAction: UpdateAction): Unit

  def version: String

  def findMissingOverlayRef(configs: sci.Seq[OverlayRef]): Option[OverlayRef] =
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
              case Some(rc) =>
                // check existence of overlays
                findMissingOverlayRef(rolloutProfile.overlays) match {
                  case Some(r) =>
                    reject(ValidationRejection(s"Unknown overlay ${r.name} ${r.version}"))
                  case None =>
                    // all ok, complete
                    complete {
                      log.debug("looks good, rollout can continue")
                      rolloutProfile.containerIds.foreach { containerId =>

                        // Make sure, we have the required runtime config
                        addUpdateAction(
                          containerId = containerId,
                          updateAction = AddRuntimeConfig(
                            runtimeConfig = rc
                          )
                        )

                        // Also register all required overlay configs
                        val ocs = getOverlayConfigs()
                        rolloutProfile.overlays.map { o =>
                          val oc = ocs.find(oc => oc.name == o.name && oc.version == o.version).get
                          addUpdateAction(
                            containerId = containerId,
                            updateAction = AddOverlayConfig(
                              overlay = oc
                            )
                          )
                        }

                        // finally stage the new runtime config
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

  def uploadDeploymentPackRoute: Route = {
    path("profile" / "upload" / "deploymentpack" / Segment) { repoId =>
      post {
        log.debug(s"upload to repo [${repoId}] requested. Checking permissions...")
        //        requirePermission("profile:update") {
        //          requirePermission(s"repository:upload:${repoId}") {
        uploadedFile("file") {
          case (metadata, file) =>
            try {
              processDeploymentPack(repoId, file) match {
                case Success(profile) =>
                  complete {
                    s"""Uploaded profile ${profile._1} ${profile._2}"""
                  }
                case Failure(e) =>
                  log.error(e)(s"Could not process uploaded deployment pack file [${file}]")
                  complete {
                    HttpResponse(
                      StatusCodes.UnprocessableEntity,
                      entity = s"Could not process the uploaded deployment pack file. Reason: ${e.getMessage()}"
                    )
                  }
              }
            } finally {
              file.delete()
            }
        }
        //          }
        //        }
      }
    }
  }

  /**
   * Process the input stream as it it were a ZIP file stram containing the deploymentpack for a profile.
   * If the deployment was successful, this method returns the profile name and version as tuple, else the exception is returned.
   *
   * @return Tuple of profile name and version.
   */
  def processDeploymentPack(repoId: String, zipFile: File): Try[(String, String)] = {
    log.debug(s"About to process deploymentpack as inputstream for repoId: ${repoId}")

    // create temp file to find a free name, than delete and create dir with that name
    val tempDir = File.createTempFile("upload", "")
    tempDir.delete()
    tempDir.mkdirs()

    val unzipped = Unzipper.unzip(
      archive = zipFile,
      targetDir = tempDir,
      selectedFiles = List(),
      fileSelector = None,
      placeholderReplacer = None
    )

    val result = unzipped.flatMap { files =>
      Try {
        log.debug(s"Extraced files: ${files}")
        val profileConfFile = files.find(f => f.getName() == "profile.conf").get
        val config = ConfigFactory.parseFile(profileConfFile)
        val local = RuntimeConfigCompanion.
          read(config).
          flatMap(_.resolve()).
          flatMap(c => Try { LocalRuntimeConfig(c, tempDir) }).
          get

        val issues =
          local.validate(includeResourceArchives = true, explodedResourceArchives = false) ++
            local.resolvedRuntimeConfig.allBundles.filter(b => !b.url.startsWith("mvn:")).map(u => s"Unsupported bundle URL: ${u}") ++
            local.runtimeConfig.resources.filter(b => !b.url.startsWith("mvn:")).map(u => s"Unsupported resource URL: ${u}")

        if (!issues.isEmpty) sys.error(issues.mkString("; "))

        // everything is ok
        log.debug(s"Uploaded profile.conf: ${local}")
        registerRuntimeConfig(local.runtimeConfig)

        // we know the urls all start with "mvn:"
        // now install bundles
        local.resolvedRuntimeConfig.allBundles.map { b =>
          val file = local.bundleLocation(b)
          val path = MvnGav.parse(b.url.substring("mvn:".size)).get.toUrl("")
          installBundle(repoId, path, file, b.artifact.sha1Sum)
        }
        // install resources
        local.runtimeConfig.resources.map { r =>
          val file = local.resourceArchiveLocation(r)
          val path = MvnGav.parse(r.url.substring("mvn:".size)).get.toUrl("")
          installBundle(repoId, path, file, r.sha1Sum)
        }
        local.runtimeConfig.name -> local.runtimeConfig.version
      }
    }
    deleteRecursive(tempDir)
    result
  }

  def deleteRecursive(files: File*): Unit = files.foreach { file =>
    if (file.isDirectory()) {
      file.listFiles() match {
        case null =>
        case files => deleteRecursive(files: _*)
      }
    }
    file.delete()
  }

  /**
   * Install the file under path. If there is an collision, only reject the file if the sha1Sum does not compare equal.
   */
  def installBundle(repoId: String, path: String, file: File, sha1Sum: Option[String]): Try[Unit]

}
