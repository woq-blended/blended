package blended.updater.remote

import blended.persistence.PersistenceService

import scala.collection.JavaConverters._
import scala.util.Try

import blended.updater.config._
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

class PersistentContainerStatePersistor(persistenceService: PersistenceService) extends ContainerStatePersistor {

  private[this] val log = LoggerFactory.getLogger(classOf[PersistentContainerStatePersistor])

  val pClassName = "ContainerState"

  override def findAllContainerStates(): List[ContainerState] = {
    val state = persistenceService.findAll(pClassName)
    state.flatMap(s => Mapper.unmapContainerState(s).toOption).toList
  }

  override def findContainerState(containerId: String): Option[ContainerState] = {
    // TODO: ensure, we take the newest
    val state = persistenceService.findByExample(pClassName, Map("containerId" -> containerId).asJava)
    state.flatMap(s => Mapper.unmapContainerState(s).toOption).headOption
  }

  override def updateContainerState(containerState: ContainerState): Unit = {
    log.debug("About to persist container state: {}", containerState)
    persistenceService.persist(pClassName, Mapper.mapContainerState(containerState))
  }
}