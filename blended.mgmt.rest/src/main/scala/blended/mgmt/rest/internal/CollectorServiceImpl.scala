package blended.mgmt.rest.internal

import scala.collection.immutable

import akka.event.EventStream
import blended.mgmt.repo.WritableArtifactRepo
import blended.prickle.akka.http.PrickleSupport
import blended.security.BlendedPermissionManager
import blended.security.akka.http.JAASSecurityDirectives
import blended.updater.config._
import blended.util.logging.Logger

/**
 * @param remoteContainerStatePersistor
 *   Used to persist remote container states,
 *   so we can remember all last seen container states.
 * @param mgr
 * @param version
 */
class CollectorServiceImpl(
    remoteContainerStatePersistor: RemoteContainerStatePersistor,
    override val mgr: BlendedPermissionManager,
    override val version: String
) extends CollectorService
    with JAASSecurityDirectives
    with PrickleSupport {

  private[this] lazy val log = Logger[this.type]
  log.info(s"This is ${toString()}")

  override def toString(): String =
    getClass().getSimpleName() +
      "(remoteContainerStatePersistor=" + remoteContainerStatePersistor +
      ",mgr=" + mgr +
      ",version=" + version +
      ")"

  private[this] var eventStream: Option[EventStream] = None

  private[this] var artifactRepos: Map[String, WritableArtifactRepo] = Map()

  def addArtifactRepo(repo: WritableArtifactRepo): Unit = {
    artifactRepos += repo.repoId -> repo
  }

  def removeArtifactRepo(repo: WritableArtifactRepo): Unit = {
    artifactRepos = artifactRepos.view.filterKeys(name => name != repo.repoId).toMap
  }

  def setEventStream(eventStream: Option[EventStream]): Unit = this.eventStream = eventStream

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    log.debug(s"Processing container info: ${info}")

    eventStream.foreach {
      _.publish(UpdateContainerInfo(info))
    }

    val state = RemoteContainerState(info)
    remoteContainerStatePersistor.updateState(state)

    // ...and send these outstanding actions to the remote container
    ContainerRegistryResponseOK(info.containerId)
  }

  override def getCurrentState(): immutable.Seq[RemoteContainerState] = {
    val states = remoteContainerStatePersistor.findAll()
    log.debug(s"About to send state: ${states}")
    states
  }

}
