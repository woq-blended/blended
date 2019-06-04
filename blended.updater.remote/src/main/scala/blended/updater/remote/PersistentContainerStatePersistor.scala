package blended.updater.remote

import blended.persistence.PersistenceService
import blended.util.logging.Logger

import scala.collection.JavaConverters._

class PersistentContainerStatePersistor(persistenceService : PersistenceService) extends ContainerStatePersistor {

  import PersistentContainerStatePersistor._

  private[this] val log = Logger[PersistentContainerStatePersistor]

  override def findAllContainerStates() : List[ContainerState] = {
    val state = persistenceService.findAll(pClassName)
    state.flatMap(s => Mapper.unmapContainerState(s).toOption).toList
  }

  override def findContainerState(containerId : String) : Option[ContainerState] = {
    // TODO: ensure, we take the newest
    val state = persistenceService.findByExample(pClassName, Map("containerId" -> containerId).asJava)
    state.flatMap(s => Mapper.unmapContainerState(s).toOption).headOption
  }

  override def updateContainerState(containerState : ContainerState) : Unit = {
    log.debug(s"About to update (=delete/persist) container state: ${containerState}")
    val deleteCount = persistenceService.deleteByExample(pClassName, Map("containerId" -> containerState.containerId).asJava)
    log.debug(s"deleted ${deleteCount} old entries")
    val entry = persistenceService.persist(pClassName, Mapper.mapContainerState(containerState))
    log.debug(s"persisted 1 new entry: ${entry}")
  }
}

object PersistentContainerStatePersistor {
  val pClassName = "ContainerState"
}
