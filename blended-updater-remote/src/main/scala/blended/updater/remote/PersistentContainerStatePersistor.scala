package blended.updater.remote

import blended.persistence.PersistenceService
import scala.collection.JavaConverters._
import scala.util.Try
import blended.mgmt.base.AddRuntimeConfig
import blended.mgmt.base.AddOverlayConfig
import blended.mgmt.base.StageProfile
import blended.mgmt.base.ActivateProfile
import scala.collection.immutable
import blended.updater.config.RuntimeConfig
import blended.updater.config.OverlayConfig

class PersistentContainerStatePersistor(persistenceService: PersistenceService) extends ContainerStatePersistor {

  val pClassName = "ContainerState"

  def fromContainerState(containerState: ContainerState): java.util.Map[String, AnyRef] = {
    Map(
      "containerId" -> containerState.containerId,
      "outstandingActions" -> containerState.outstandingActions.map {
        case a: AddRuntimeConfig =>
          Map(
            "kind" -> a.kind,
            "runtimeConfig" -> RuntimeConfig.toConfig(a.runtimeConfig).root().unwrapped()
          ).asJava
        case a: AddOverlayConfig =>
          Map(
            "kind" -> a.kind,
            "overlay" -> OverlayConfig.toConfig(a.overlay).root().unwrapped()
          ).asJava
        case s: StageProfile =>
          Map(
            "kind" -> s.kind,
            "profileName" -> s.profileName,
            "profileVersion" -> s.profileVersion,
            "overlays" -> s.overlays.map { o =>
              Map("name" -> o.name,
                "version" -> o.version
              ).asJava
            }.asJava
          ).asJava
        case a: ActivateProfile =>
          Map("kind" -> a.kind,
            "profileName" -> a.profileName,
            "profileVersion" -> a.profileVersion,
            "overlays" -> a.overlays.map { o =>
              Map("name" -> o.name,
                "version" -> o.version
              ).asJava
            }.asJava
          ).asJava
      }.asJava,
      "activeProfile" -> containerState.activeProfile.orNull,
      "validProfiles" -> containerState.validProfiles.asJava,
      "invalidProfiles" -> containerState.invalidProfiles.asJava,
      "syncTimeStamp" -> containerState.syncTimeStamp.map(l => java.lang.Long.valueOf(l)).orNull).asJava
  }

  def toContainerState(map: java.util.Map[String, _ <: AnyRef]): Try[ContainerState] = {
    ???
  }

  override def findAllContainerStates(): immutable.Seq[ContainerState] = {
    val state = persistenceService.findAll(pClassName)
    state.flatMap(s => toContainerState(s).toOption).toList
  }

  override def findContainerState(containerId: String): Option[ContainerState] = {
    val state = persistenceService.findByExample(pClassName, Map("containerId" -> containerId).asJava)
    state.flatMap(s => toContainerState(s).toOption).headOption
  }

  override def updateContainerState(containerState: ContainerState): Unit = {
    persistenceService.persist(pClassName, fromContainerState(containerState))
  }

}