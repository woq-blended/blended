package blended.mgmt.rest.internal

import java.io.File

import akka.event.EventStream
import blended.mgmt.repo.WritableArtifactRepo
import blended.prickle.akka.http.PrickleSupport
import blended.security.BlendedPermissionManager
import blended.security.akka.http.JAASSecurityDirectives
import blended.updater.config._
import blended.updater.remote.RemoteUpdater
import blended.util.logging.Logger

import scala.collection.immutable
import scala.util.Try

/**
 *
 * @param updater
 *   The RemoteUpdater which manages which container should run with which profile.
 * @param remoteContainerStatePersistor
 *   Used to persist remote container states,
 *   so we can remember all last seen container states.
 * @param mgr
 * @param version
 */
class CollectorServiceImpl(
  updater : RemoteUpdater,
  remoteContainerStatePersistor : RemoteContainerStatePersistor,
  override val mgr : BlendedPermissionManager,
  override val version : String
)
  extends CollectorService
  with JAASSecurityDirectives
  with PrickleSupport {

  private[this] lazy val log = Logger[this.type]
  log.info(s"This is ${toString()}")

  override def toString() : String = getClass().getSimpleName() +
    "(updater=" + updater +
    ",remoteContainerStatePersistor=" + remoteContainerStatePersistor +
    ",mgr=" + mgr +
    ",version=" + version +
    ")"

  private[this] var eventStream : Option[EventStream] = None

  private[this] var artifactRepos : Map[String, WritableArtifactRepo] = Map()

  def addArtifactRepo(repo : WritableArtifactRepo) : Unit = {
    artifactRepos += repo.repoId -> repo
  }

  def removeArtifactRepo(repo : WritableArtifactRepo) : Unit = {
    artifactRepos = artifactRepos.filterKeys(name => name != repo.repoId).toMap
  }

  def setEventStream(eventStream : Option[EventStream]) : Unit = this.eventStream = eventStream

  override def processContainerInfo(info : ContainerInfo) : ContainerRegistryResponseOK = {
    log.debug(s"Processing container info: ${info}")

    eventStream.foreach {
      _.publish(UpdateContainerInfo(info))
    }

    // next call has side-effect
    val updated = updater.updateContainerState(info)
    // we now have updated outstanding actions
    val actions = updated.outstandingActions

    val state = RemoteContainerState(info, actions)
    remoteContainerStatePersistor.updateState(state)

    // ...and send these outstanding actions to the remote container
    ContainerRegistryResponseOK(info.containerId, actions)
  }

  override def getCurrentState() : immutable.Seq[RemoteContainerState] = {
    val states = remoteContainerStatePersistor.findAll()
    log.debug(s"About to send state: ${states}")
    states
  }

  override def registerRuntimeConfig(rc : RuntimeConfig) : Unit = updater.registerRuntimeConfig(rc)

  override def registerOverlayConfig(oc : OverlayConfig) : Unit = updater.registerOverlayConfig(oc)

  override def getRuntimeConfigs() : immutable.Seq[RuntimeConfig] = updater.getRuntimeConfigs()

  override def getOverlayConfigs() : immutable.Seq[OverlayConfig] = updater.getOverlayConfigs()

  override def addUpdateAction(containerId : String, updateAction : UpdateAction) : Unit = updater.addAction(containerId, updateAction)

  override def installBundle(repoId : String, path : String, file : File, sha1Sum : Option[String]) : Try[Unit] = Try {
    log.debug(s"About to install bundle into repoId: ${repoId} at path: ${path}, file: ${file} with checksum: ${sha1Sum}")
    val repo = artifactRepos.getOrElse(repoId, sys.error(s"No artifact repository with ID ${repoId} registered"))
    val stream = file.toURI().toURL().openStream()
    try {
      repo.uploadFile(path, stream, sha1Sum).get
    } finally {
      stream.close()
    }
  }

}
