package blended.mgmt.rest.internal

import blended.akka.OSGIActorConfig
import blended.spray.{ SprayOSGIServlet, SprayPrickleSupport }
import blended.updater.config._
import blended.updater.remote.RemoteUpdater
import org.slf4j.LoggerFactory

import scala.collection.immutable
import blended.security.spray.ShiroBlendedSecuredRoute
import blended.persistence.PersistenceService
import blended.mgmt.repo.ArtifactRepo
import domino.capsule.CapsuleConvenience
import java.io.File
import scala.util.Try
import domino.service_watching.ServiceWatcherEvent
import blended.mgmt.repo.WritableArtifactRepo

class ManagementCollectorServlet
    extends SprayOSGIServlet
    with CollectorService
    with SprayPrickleSupport
    with ShiroBlendedSecuredRoute
    with CapsuleConvenience {

  type ContainerId = String

  private[this] val mylog = LoggerFactory.getLogger(classOf[ManagementCollectorServlet])

  private[this] var containerStates: Map[ContainerId, RemoteContainerState] = Map()

  private[this] var remoteUpdater: Option[RemoteUpdater] = None
  private[this] var remoteContainerStatePersistor: Option[RemoteContainerStatePersistor] = None
  private[this] var artifactRepos: Map[String, WritableArtifactRepo] = Map()

  private[this] var cfg: Option[OSGIActorConfig] = None

  override def startSpray(): Unit = {

    whenServicesPresent[RemoteUpdater, PersistenceService] { (updater, persistenceService) =>
      this.remoteUpdater = Option(updater)
      this.remoteContainerStatePersistor = Option(new RemoteContainerStatePersistor(persistenceService))
      val actor = createServletActor()

      watchServices[WritableArtifactRepo] {
        case ServiceWatcherEvent.AddingService(service, context) =>
          val repoId = service.repoId
          log.debug("Adding repo: {}", repoId)
          this.artifactRepos += repoId -> service
        case ServiceWatcherEvent.RemovedService(service, context) =>
          val repoId = service.repoId
          log.debug("Removing repo: {}", repoId)
          this.artifactRepos = artifactRepos.filter(p => p._1 == repoId)
        case ServiceWatcherEvent.ModifiedService(service, context) =>
          val repoId = service.repoId
          log.debug("Modifying repo: {}", repoId)
          this.artifactRepos += repoId -> service
      }

      // initially get all services of this type
      this.artifactRepos = services[WritableArtifactRepo].map { s => s.repoId -> s }.toMap
      log.debug("Initially starting with repos: {}", artifactRepos)

      onStop {
        stopServletActor()
        // drop all services of this type
        this.artifactRepos = Map()
        this.remoteUpdater = None
        this.remoteContainerStatePersistor = None
      }
    }
  }

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    mylog.debug("Processing container info: {}", info)
    cfg.foreach(_.system.eventStream.publish(UpdateContainerInfo(info)))

    val newActions = remoteUpdater match {
      case None =>
        mylog.warn(s"Process container info called with no remote updater available.")
        List.empty
      case Some(updater) =>
        val updated = updater.updateContainerState(info)
        val actions = updated.outstandingActions

        remoteContainerStatePersistor.map { p =>
          val state = RemoteContainerState(info, actions)
          p.updateState(state)
        }

        actions
    }

    ContainerRegistryResponseOK(info.containerId, newActions)
  }

  override def version: String = cfg match {
    case None => "UNKNOWN"
    case Some(c) => c.bundleContext.getBundle().getVersion().toString()
  }

  override def getCurrentState(): immutable.Seq[RemoteContainerState] = {
    val states = remoteContainerStatePersistor.map { ps =>
      ps.findAll()
    }.getOrElse(List())
    mylog.debug("About to send state: {}", containerStates)
    states
  }

  override def registerRuntimeConfig(rc: RuntimeConfig): Unit = remoteUpdater match {
    case None => throw new Exception("registerRuntimeConfig called with no remote updater available")
    case Some(updater) => updater.registerRuntimeConfig(rc)
  }

  override def registerOverlayConfig(oc: OverlayConfig): Unit = remoteUpdater match {
    case None => throw new Exception("registerOverlayConfig called with no remote updater available")
    case Some(updater) => updater.registerOverlayConfig(oc)
  }

  override def getRuntimeConfigs(): immutable.Seq[RuntimeConfig] = remoteUpdater match {
    case None => immutable.Seq.empty[RuntimeConfig]
    case Some(updater) => updater.getRuntimeConfigs()
  }

  override def getOverlayConfigs(): immutable.Seq[OverlayConfig] = remoteUpdater match {
    case None => immutable.Seq.empty[OverlayConfig]
    case Some(updater) => updater.getOverlayConfigs()
  }

  override def addUpdateAction(containerId: String, updateAction: UpdateAction): Unit = remoteUpdater match {
    case None => throw new Exception("addUpdateAction called with no remote updater available")
    case Some(updater) => updater.addAction(containerId, updateAction)
  }

  override def installBundle(repoId: String, path: String, file: File, sha1Sum: Option[String]): Try[Unit] = Try {
    mylog.debug("About to install bundle into repoId: {} at path: {}, file: {} with checksum: {}", Array(repoId, path, file, sha1Sum): _*)
    val repo = artifactRepos.getOrElse(repoId, sys.error(s"No artifact repository with ID ${repoId} registered"))
    val stream = file.toURI().toURL().openStream()
    try {
      repo.uploadFile(path, stream, sha1Sum).get
    } finally {
      stream.close()
    }
  }
}
