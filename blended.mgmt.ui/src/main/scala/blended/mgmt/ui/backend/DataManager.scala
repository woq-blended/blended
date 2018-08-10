package blended.mgmt.ui.backend

import blended.mgmt.ui.ConsoleSettings
import blended.mgmt.ui.util.Logger
import blended.updater.config._
import blended.updater.config.json.PrickleProtocol._
import org.scalajs.dom.ext.Ajax
import prickle._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

object DataManager {

  private[this] val log = Logger[DataManager.type]

  object containerData extends Observable[List[ContainerInfo]] {

    override var data: List[ContainerInfo] = List.empty

    override def refresh(): Unit = {

      Ajax.get(ConsoleSettings.containerDataUrl).onComplete {
        case Success(xhr) =>
          update(Unpickle[List[RemoteContainerState]].fromString(xhr.responseText).get.map(_.containerInfo))
        case _ => log.error("Could not retrieve container list from server")
      }
    }
  }

  object serviceData extends Observable[List[ServiceInfo]] {

    override var data: List[ServiceInfo] = List.empty

    override def refresh(): Unit = {

      Ajax.get(ConsoleSettings.containerDataUrl).onComplete {
        case Success(xhr) =>
          val ctData = Unpickle[List[RemoteContainerState]].fromString(xhr.responseText).get.map(_.containerInfo)
          update(ctData.flatMap(ct => ct.serviceInfos))
        case _ => log.error("Could not retrieve container list from server")
      }
    }
  }

  object profilesData extends Observable[List[Profile]] {

    override var data: List[Profile] = List.empty

    override def refresh(): Unit = {
      Ajax.get(ConsoleSettings.profilesUrl).onComplete {
        case Success(xhr) =>
          update(Unpickle[ProfileInfo].fromString(xhr.responseText).get.profiles)
        case _ => log.error("Could not retrieve profile list from server")
      }
    }
  }

  object runtimeConfigsData extends Observable[List[RuntimeConfig]] {

    override var data: List[RuntimeConfig] = List.empty

    override def refresh(): Unit = {
      Ajax.get(ConsoleSettings.runtimeConfigsUrl).onComplete {
        case Success(xhr) =>
          update(Unpickle[List[RuntimeConfig]].fromString(xhr.responseText).get)
        case _ => log.error("Could not retrieve runtime config list from server")
      }
    }
  }

  object overlayConfigsData extends Observable[List[OverlayConfig]] {

    override var data: List[OverlayConfig] = List.empty

    override def refresh(): Unit = {
      Ajax.get(ConsoleSettings.overlayConfigUrl).onComplete {
        case Success(xhr) =>
          update(Unpickle[List[OverlayConfig]].fromString(xhr.responseText).get)
        case _ => log.error("Could not retrieve overlay list from server")
      }
    }
  }
}
