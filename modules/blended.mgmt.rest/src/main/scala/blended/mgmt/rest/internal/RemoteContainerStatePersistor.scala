package blended.mgmt.rest.internal

import blended.persistence.PersistenceService
import blended.updater.config.{Mapper, RemoteContainerState}
import blended.util.logging.Logger

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

class RemoteContainerStatePersistor(persistenceService : PersistenceService) {

  import RemoteContainerStatePersistor._

  private[this] val log = Logger[RemoteContainerStatePersistor]

  def findAll() : List[RemoteContainerState] = {
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

  def updateState(state : RemoteContainerState) : Unit = {
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

object RemoteContainerStatePersistor {
  val pClass = "RemoteContainerState"
}
