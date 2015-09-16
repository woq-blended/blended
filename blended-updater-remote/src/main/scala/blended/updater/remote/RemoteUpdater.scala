package blended.updater.remote

import blended.updater.config.RuntimeConfig
import blended.container.registry.protocol.ContainerInfo
import scala.collection.immutable

trait RemoteUpdater {

  type ContainerId = String

  //  case class State[T](actionsPerContainer: Map[T, UpdateAction])

  //  private[this] var state: State[T] = State(Map())

  //  case class ContainerActions(containerId: T, actions: Seq[UpdateAction])

  private[this] var state: Map[ContainerId, immutable.Seq[UpdateAction]] = Map()

  def addAction(containerId: ContainerId, action: UpdateAction): Unit = {
    val actions = state.getOrElse(containerId, immutable.Seq())
    val newActions =
      if (!actions.exists { _ == action }) { actions ++ immutable.Seq(action) }
      else actions
    state += containerId -> newActions
  }

  def removeResolvedActions(containerInfo: ContainerInfo): Unit = {
    state.getOrElse(containerInfo.containerId, immutable.Seq()) match {
      case immutable.Seq() =>
      // No state saved, no need to examine the container info
      case updateActions =>
        containerInfo.serviceInfos.find(_.name == "blended/updater") match {
          case None =>
          // no updater service infos
          case Some(si) =>
            val props = si.props
            val active = props.getOrElse("profile.active", "").trim()
            val staged = props.getOrElse("profiles.valid", "").split(",").map(_.trim()).filter(!_.isEmpty)
            val newUpdateActions = updateActions.filter {
              case StageProfile(rc) => !staged.exists(_ == s"${rc.name}-${rc.version}")
              case ActivateProfile(n, v) => active != s"${n}-${v}"
              case _ => true
            }
            state += containerInfo.containerId -> newUpdateActions
        }
    }
  }

  def getContainerActions(containerId: ContainerId): immutable.Seq[UpdateAction] = state.getOrElse(containerId, immutable.Seq())

}

sealed trait UpdateAction
final case class StageProfile(runtimeConfig: RuntimeConfig) extends UpdateAction
final case class ActivateProfile(profileName: String, profileVersion: String) extends UpdateAction

