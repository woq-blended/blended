package blended.mgmt.rest.internal

import blended.persistence.PersistenceService
import scala.collection.JavaConverters._
import org.slf4j.LoggerFactory
import blended.updater.config.RemoteContainerState
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import blended.updater.config.Mapper

class RemoteContainerStatePersistor(persistenceService: PersistenceService) {

  private[this] val log = LoggerFactory.getLogger(classOf[RemoteContainerStatePersistor])

  val pClass = "RemoteContainerState"

  def findAll(): List[RemoteContainerState] = {
    val result = persistenceService.findAll(pClass)
    log.debug("loaded {} entries from db", result.size)
    result.toList.flatMap { s =>
      Mapper.unmapRemoteContainerState(s) match {
        case Success(s) => List(s)
        case Failure(e) =>
          log.warn("Could not create RemoteContainerState from persisted map (ignoring this entry): {}", Array[Object](s, e): _*)
          List()
      }
    }
  }

  def updateState(state: RemoteContainerState): Unit = {
    log.debug("About to persist remote container state: {}", state)
    val deleteCount = persistenceService.deleteByExample(pClass, Map("containerInfo.containerId" -> state.containerInfo.containerId).asJava)
    log.debug("deleted {} old entries", deleteCount)
    val entry = persistenceService.persist(pClass, Mapper.mapRemoteContainerState(state))
    log.debug("persisted 1 new entry: {}", entry)
  }

}