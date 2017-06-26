package blended.mgmt.ui.backend

import blended.mgmt.ui.ConsoleSettings
import blended.mgmt.ui.pages.{ TopLevelPage, TopLevelPages }
import blended.updater.config.ContainerInfo
import blended.updater.config.json.PrickleProtocol._
import org.scalajs.dom.ext.Ajax
import prickle._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import blended.mgmt.ui.util.Logger
import blended.updater.config.RemoteContainerState
import blended.updater.config.Profile
import blended.updater.config.ProfileInfo
import blended.updater.config.ProfileInfo
import blended.updater.config.OverlaySet
import blended.updater.config.OverlayRef
import blended.updater.config.OverlayState
import blended.updater.config.RuntimeConfig
import blended.updater.config.OverlayConfig

object DataManager {

  private[this] val log = Logger[DataManager.type]

  var selectedPage: TopLevelPage = TopLevelPages.defaultPage

  def setSelectedPage(page: TopLevelPage): Unit = {
    log.debug("Selected Page : " + page.routerPath.toString)
    selectedPage = page
  }

  object containerData extends Observable[List[ContainerInfo]] {

    override var data: List[ContainerInfo] = List.empty

    override def refresh(): Unit = {

      Ajax.get(ConsoleSettings.containerDataUrl).onComplete {
        case Success(xhr) =>
          log.trace("response: " + xhr.responseText)
          update(Unpickle[List[RemoteContainerState]].fromString(xhr.responseText).get.map(_.containerInfo))
        case _ => log.error("Could not retrieve container list from server")
      }
    }

  }

  object profilesData extends Observable[List[Profile]] {

    override var data: List[Profile] = List( //      Profile("profileA", "1.0", List()),
    //      Profile("profileB", "1.0", List()),
    //      Profile("profileA", "2.0", List(OverlaySet(List(OverlayRef("ov", "1")), state = OverlayState.Pending)))
    )

    override def refresh(): Unit = {
      update(data)
      Ajax.get(ConsoleSettings.profilesUrl).onComplete {
        case Success(xhr) =>
          log.trace("response: " + xhr.responseText)
          update(Unpickle[ProfileInfo].fromString(xhr.responseText).get.profiles)
        case _ => log.error("Could not retrieve profile list from server")
      }
    }
  }

  object runtimeConfigsData extends Observable[List[RuntimeConfig]] {

    override var data: List[RuntimeConfig] = List()

    override def refresh(): Unit = {
      update(data)
      Ajax.get(ConsoleSettings.runtimeConfigsUrl).onComplete {
        case Success(xhr) =>
          log.trace("response: " + xhr.responseText)
          update(Unpickle[List[RuntimeConfig]].fromString(xhr.responseText).get)
        case _ => log.error("Could not retrieve runtime config list from server")
      }
    }
  }

  object overlayConfigsData extends Observable[List[OverlayConfig]] {

    override var data: List[OverlayConfig] = List()

    override def refresh(): Unit = {
      update(data)
      Ajax.get(ConsoleSettings.overlayConfigUrl).onComplete {
        case Success(xhr) =>
          log.trace("response: " + xhr.responseText)
          update(Unpickle[List[OverlayConfig]].fromString(xhr.responseText).get)
        case _ => log.error("Could not retrieve overlay list from server")
      }
    }
  }

  
}

