package blended.mgmt.ui.backend

import blended.mgmt.ui.ConsoleSettings
import blended.mgmt.ui.pages.{TopLevelPage, TopLevelPages}
import blended.updater.config.ContainerInfo
import blended.updater.config.json.PrickleProtocol._
import org.scalajs.dom.ext.Ajax
import prickle._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import blended.mgmt.ui.util.Logger


object DataManager {

  private[this] val log = Logger[DataManager.type]
  
  var selectedPage : TopLevelPage = TopLevelPages.defaultPage

  def setSelectedPage(page: TopLevelPage) : Unit = {
    log.debug("Selected Page : " + page.routerPath.toString)
    selectedPage = page
  }

  object containerData extends Observable[List[ContainerInfo]] {

    override var data: List[ContainerInfo] = List.empty

    override def refresh(): Unit = {

      Ajax.get(ConsoleSettings.mgmtUrl).onComplete {
        case Success(xhr) =>
          log.trace("response: " + xhr.responseText)
          update(Unpickle[List[ContainerInfo]].fromString(xhr.responseText).get)
        case _ => log.error("Could not retrieve container list from server")
      }
    }

  }
}

