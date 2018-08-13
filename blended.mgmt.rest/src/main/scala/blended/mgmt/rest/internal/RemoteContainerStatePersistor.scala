package blended.mgmt.rest.internal

import scala.collection.JavaConverters._
import scala.util.Failure
import scala.util.Success

import blended.persistence.PersistenceService
import blended.updater.config.Mapper
import blended.updater.config.RemoteContainerState
import blended.util.logging.Logger

class RemoteContainerStatePersistor(persistenceService: PersistenceService) {

  private[this] val log = Logger[RemoteContainerStatePersistor]

  val pClass = "RemoteContainerState"

  def findAll(): List[RemoteContainerState] = {
    val result = persistenceService.findAll(pClass)
    log.debug(s"loaded ${result.size} entries from db")
    result.toList.flatMap { s =>
      Mapper.unmapRemoteContainerState(s) match {
        case Success(s) => List(s)
        case Failure(e) =>
          log.warn(e)(s"Could not create RemoteContainerState from persisted map (ignoring this entry): ${s}")
          List()
      }
    }
  }

  def updateState(state: RemoteContainerState): Unit = {
    log.debug(s"About to persist remote container state: ${state}")
    val deleteCount = persistenceService.deleteByExample(pClass, Map(
      "containerInfo" -> Map(
        "containerId" -> state.containerInfo.containerId
      ).asJava
    ).asJava)
    log.debug(s"deleted ${deleteCount} old entries")
    val entry = persistenceService.persist(pClass, Mapper.mapRemoteContainerState(state))
    log.debug(s"persisted 1 new entry: ${entry}")
  }

}