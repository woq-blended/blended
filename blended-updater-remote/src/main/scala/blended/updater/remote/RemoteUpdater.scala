package blended.updater.remote

import blended.mgmt.base.ContainerInfo
import blended.updater.config.RuntimeConfig
import scala.collection.immutable
import blended.mgmt.base.StageProfile
import blended.mgmt.base.UpdateAction
import blended.mgmt.base.ActivateProfile
import org.slf4j.LoggerFactory

trait RemoteUpdater { self: ActionPersistor with RuntimeConfigPersistor =>

  private[this] val log = LoggerFactory.getLogger(classOf[RemoteUpdater])

  type ContainerId = String

  def addAction(containerId: ContainerId, action: UpdateAction): Unit = {
    val actions = self.findActions(containerId)
    val newActions =
      if (!actions.exists { _ == action }) { actions ++ immutable.Seq(action) }
      else actions
    self.updateActions(containerId, newActions)
  }

  def removeResolvedActions(containerInfo: ContainerInfo): Unit = {
    log.debug(s"About to analyze update properties from container info: ${containerInfo}")
    self.findActions(containerInfo.containerId) match {
      case immutable.Seq() =>
        log.debug(s"No update actions recorded for container with ID ${containerInfo.containerId}")
      // No state saved, no need to examine the container info
      case updateActions =>
        val props = containerInfo.serviceInfos.find(_.name.endsWith("/blended.updater")).map(si => si.props).getOrElse(Map())
        val active = props.getOrElse("profile.active", "").trim()
        val staged = props.getOrElse("profiles.valid", "").split(",").map(_.trim()).filter(!_.isEmpty)
        val newUpdateActions = updateActions.filter {
          case StageProfile(rc) => !staged.exists(_ == s"${rc.name}-${rc.version}")
          case ActivateProfile(n, v) => active != s"${n}-${v}"
          case _ => true
        }
        self.updateActions(containerInfo.containerId, newUpdateActions)
    }
  }

  def getContainerActions(containerId: ContainerId): immutable.Seq[UpdateAction] = self.findActions(containerId)

  def getContainerIds(): immutable.Seq[ContainerId] = self.findIds()

  def registerRuntimeConfig(runtimeConfig: RuntimeConfig): Unit = self.persistRuntimeConfig(runtimeConfig)

  def getRuntimeConfigs(): immutable.Seq[RuntimeConfig] = self.findRuntimeConfigs()

}

trait ActionPersistor {
  type ContainerId
  def findIds(): immutable.Seq[ContainerId]
  def findActions(containerId: ContainerId): immutable.Seq[UpdateAction]
  def updateActions(containerId: ContainerId, actions: immutable.Seq[UpdateAction]): Unit
}

trait RuntimeConfigPersistor {
  def persistRuntimeConfig(runtimeConfig: RuntimeConfig): Unit
  def findRuntimeConfigs(): immutable.Seq[RuntimeConfig]
}

trait InMemoryActionPersistor extends ActionPersistor {

  private[this] var state: Map[ContainerId, immutable.Seq[UpdateAction]] = Map()

  override def findIds(): immutable.Seq[ContainerId] = state.keySet.to[immutable.Seq]

  override def findActions(containerId: ContainerId): immutable.Seq[UpdateAction] = state.getOrElse(containerId, immutable.Seq())

  override def updateActions(containerId: ContainerId, actions: immutable.Seq[UpdateAction]): Unit = state += containerId -> actions
}

trait InMemoryRuntimeConfigPersistor extends RuntimeConfigPersistor {

  private[this] var state: Set[RuntimeConfig] = Set()

  override def persistRuntimeConfig(runtimeConfig: RuntimeConfig): Unit = state += runtimeConfig

  override def findRuntimeConfigs(): immutable.Seq[RuntimeConfig] = state.to[immutable.Seq]
}

trait InMemoryPersistor extends InMemoryActionPersistor with InMemoryRuntimeConfigPersistor