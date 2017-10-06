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
import spray.routing.ValidationRejection
import spray.http.HttpHeader
import spray.http.HttpHeaders
import spray.http.AllOrigins
import spray.http.MultipartFormData
import spray.http.BodyPart
import java.io.ByteArrayInputStream
import scala.util.Try
import spray.http.HttpData
import java.io.File
import java.io.InputStream
import blended.updater.config.util.Unzipper
import scala.util.Failure
import scala.util.Success
import com.typesafe.config.ConfigFactory

trait CollectorService
    extends BlendedHttpRoute
    with BlendedSecuredRoute {
  this: SprayPrickleSupport =>

  override val httpRoute: Route =
    respondWithSingletonHeader(HttpHeaders.`Access-Control-Allow-Origin`(AllOrigins)) {
      collectorRoute ~
        infoRoute ~
        versionRoute ~
        runtimeConfigRoute ~
        overlayConfigRoute ~
        updateActionRoute ~
        rolloutProfileRoute
    }

  private[this] lazy val log = LoggerFactory.getLogger(classOf[CollectorService])

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

  def jsonReponse = respondWithMediaType(MediaTypes.`application/json`)

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
        jsonReponse {
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
                    reject(ValidationRejection(s"Unknown vverlay ${r.name} ${r.version}"))
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
                            overlays = rolloutProfile.overlays))
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
        requirePermission("profile:update") {
          requirePermission("repository:upload:" + repoId) {
            entity(as[MultipartFormData]) { formData =>
              detach() {
                complete {
                  val details = formData.fields.map {
                    case BodyPart(entity, headers) =>
                      // Warning, this loads the whole file into memory
                      //                      val content = new ByteArrayInputStream(entity.data.toByteArray)
                      //                      val contentType = headers.find(h => h.is("content-type")).get.value
                      //                      val fileName = headers.find(h => h.is("content-disposition")).get.value.split("filename=").last
                      //                      val result = processDeploymentpack(content)
                      //                      (contentType, result)

                      val byteArray = entity.data.toByteArray
                      val baip = new ByteArrayInputStream(byteArray)
                      val result = try {
                        processDeploymentPack(repoId, baip)
                      } finally {
                        baip.close()
                      }

                      val profile = result.get
                      s"Uploaded profile ${profile._1 + " " + profile._2}"
                    case _ =>
                  }
                  s"""{"status": "Processed POST request, details=$details" }"""
                }
              }
            }
          }
        }
      }

    }
  }

  /**
   * Process the input stream as it it were a ZIP file stram containing the deploymentpack for a profile.
   * If the deployment was successful, this method returns the profile name and version as tuple, else the exception is returned.
   */
  def processDeploymentPack(repoId: String, content: InputStream): Try[(String, String)] = {
    val tempDir = File.createTempFile("upload", "")
    tempDir.delete()
    tempDir.mkdirs()

    val unzipped = Unzipper.unzip(
      inputStream = content,
      targetDir = tempDir,
      selectedFiles = List(),
      fileSelector = None,
      placeholderReplacer = None,
      archive = Some("<stream>")
    )

    val result = unzipped flatMap { files =>
      Try {

        log.debug("Extraced files: {}", files)

        val profileConfFile = files.find(f => f.getName() == "profile.conf").get

        val config = ConfigFactory.parseFile(profileConfFile)
        val local = RuntimeConfigCompanion.
          read(config).
          flatMap(_.resolve()).
          flatMap(c => Try { LocalRuntimeConfig(c, tempDir) }).
          get
        val issues =
          local.validate(includeResourceArchives = true, explodedResourceArchives = false, checkPropertiesFile = false) ++
            local.resolvedRuntimeConfig.allBundles.filter(b => !b.url.startsWith("mvn:")).map(u => s"Unsupported bundle URL: ${u}") ++
            local.runtimeConfig.resources.filter(b => !b.url.startsWith("mvn:")).map(u => s"Unsupported resource URL: ${u}")

        if (!issues.isEmpty) sys.error(issues.mkString("; "))
        // everything is ok

        // TODO: install profile itself

        // now install bundles and resources
        local.resolvedRuntimeConfig.allBundles.map { b =>
          val file = local.bundleLocation(b)
          val path = b.url
          installBundle(repoId, path, file, b.artifact.sha1Sum)
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
