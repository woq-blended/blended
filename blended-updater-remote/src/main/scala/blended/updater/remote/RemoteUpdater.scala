package blended.updater.remote

import blended.mgmt.base.ContainerInfo
import blended.updater.config.RuntimeConfig
import scala.collection.immutable
import blended.mgmt.base.StageProfile
import blended.mgmt.base.UpdateAction
import blended.mgmt.base.ActivateProfile
import org.slf4j.LoggerFactory
import java.util.Date

trait RemoteUpdater { self: RuntimeConfigPersistor with ContainerStatePersistor =>

  private[this] val log = LoggerFactory.getLogger(classOf[RemoteUpdater])

  type ContainerId = String

  def addAction(containerId: ContainerId, action: UpdateAction): Unit = {
    val state = self.findContainerState(containerId).getOrElse(ContainerState(containerId = containerId))
    val actions = state.outstandingActions
    val newActions =
      if (!actions.exists { _ == action }) { actions ++ immutable.Seq(action) }
      else actions
    self.updateContainerState(state.copy(outstandingActions = newActions))
  }

  def updateContainerState(containerInfo: ContainerInfo): ContainerState = {
    log.debug(s"About to analyze update properties from container info: ${containerInfo}")
    val timeStamp = System.currentTimeMillis()
    val state = self.findContainerState(containerInfo.containerId).getOrElse(ContainerState(containerId = containerInfo.containerId))

    val props = containerInfo.serviceInfos.find(_.name.endsWith("/blended.updater")).map(si => si.props).getOrElse(Map())
    val active = props.get("profile.active").map(_.trim()).filter(!_.isEmpty())
    val valid = props.get("profiles.valid").toList.flatMap(_.split(",")).map(_.trim()).filter(!_.isEmpty())
    val invalid = props.get("profiles.invalid").toList.flatMap(_.split(",")).map(_.trim()).filter(!_.isEmpty())

    val newUpdateActions = state.outstandingActions.filter {
      // TODO: support for overlays
      case ActivateProfile(n, v, o, _) => !active.exists(_ == s"${n}-${v}")
      case StageProfile(n, v, oc, _) => !valid.exists(_ == s"${n}-${v}")
      case _ => true
    }

    val newState = state.copy(
      activeProfile = active,
      validProfiles = valid,
      invalidProfiles = invalid,
      outstandingActions = newUpdateActions,
      syncTimeStamp = Some(timeStamp)
    )
    self.updateContainerState(newState)
    newState
  }

  def getContainerState(containerId: ContainerId): Option[ContainerState] =
    self.findContainerState(containerId)

  def getContainerActions(containerId: ContainerId): immutable.Seq[UpdateAction] =
    getContainerState(containerId).map(_.outstandingActions).getOrElse(immutable.Seq())

  def getContainerIds(): immutable.Seq[ContainerId] = self.findAllContainerStates().map(_.containerId)

  def registerRuntimeConfig(runtimeConfig: RuntimeConfig): Unit = self.persistRuntimeConfig(runtimeConfig)

  def getRuntimeConfigs(): immutable.Seq[RuntimeConfig] = self.findRuntimeConfigs()

}

case class ContainerState(
    containerId: String,
    outstandingActions: immutable.Seq[UpdateAction] = immutable.Seq(),
    activeProfile: Option[String] = None,
    validProfiles: immutable.Seq[String] = immutable.Seq(),
    invalidProfiles: immutable.Seq[String] = immutable.Seq(),
    syncTimeStamp: Option[Long] = None) {

  override def toString(): String = s"${getClass().getSimpleName()}(containerId=${containerId},outstandingActions=${outstandingActions}" +
    s",activeProfile=${activeProfile},validProfiles=${validProfiles},invalidProfiles=${invalidProfiles},syncTimeStamp=${syncTimeStamp.map(s => new Date(s))})"

}

trait ContainerStatePersistor {
  def findAllContainerStates(): immutable.Seq[ContainerState]
  def findContainerState(containerId: String): Option[ContainerState]
  def updateContainerState(containerState: ContainerState): Unit
}

/**
 * Persistence handling for [RuntimeConfig]s.
 */
trait RuntimeConfigPersistor {
  def persistRuntimeConfig(runtimeConfig: RuntimeConfig): Unit
  def findRuntimeConfigs(): immutable.Seq[RuntimeConfig]
}

trait TransientRuntimeConfigPersistor extends RuntimeConfigPersistor {

  private[this] var state: immutable.Set[RuntimeConfig] = Set()

  override def persistRuntimeConfig(runtimeConfig: RuntimeConfig): Unit = state += runtimeConfig

  override def findRuntimeConfigs(): immutable.Seq[RuntimeConfig] = state.to[immutable.Seq]
}

trait TransientContainerStatePersistor extends ContainerStatePersistor {

  private[this] var state: immutable.Set[ContainerState] = Set()

  def findContainerState(containerId: String): Option[ContainerState] = {
    state.find(s => s.containerId == containerId)
  }

  def updateContainerState(containerState: ContainerState): Unit = {
    state = state.filter(_.containerId != containerState.containerId) + containerState
  }

  def findAllContainerStates(): immutable.Seq[ContainerState] = {
    state.to[immutable.Seq]
  }

}

trait TransientPersistor
  extends TransientRuntimeConfigPersistor
  with TransientContainerStatePersistor