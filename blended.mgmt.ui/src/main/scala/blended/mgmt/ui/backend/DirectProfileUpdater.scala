package blended.mgmt.ui.backend

import blended.updater.config.OverlaySet
import blended.updater.config.Profile
import blended.mgmt.ui.util.Logger
import scala.collection.immutable
import org.scalajs.dom.ext.Ajax
import blended.mgmt.ui.ConsoleSettings
import blended.updater.config.UpdateAction
import prickle.Pickle
import scala.util.Success

class DirectProfileUpdater(containerMgmtUrl: String) extends ProfileUpdater {

  private[this] val log = Logger[DirectProfileUpdater]

  def addUpdateActions(containerId: String, updateActions: immutable.Seq[UpdateAction]): Unit = {

    import scala.concurrent.ExecutionContext.Implicits.global
    import blended.updater.config.json.PrickleProtocol._

    val url = containerMgmtUrl + "/" + containerId + "/udpate"
    val data = Pickle.intoString(updateActions)
    log.trace(s"About so send POST request to url: ${url} with data: ${data}")
    Ajax.post(url, data).onComplete {
      case Success(baseUrl) =>
        log.trace("Successfully send update container request")
      case _ =>
        log.error("Could not send update container request")
    }
  }
}