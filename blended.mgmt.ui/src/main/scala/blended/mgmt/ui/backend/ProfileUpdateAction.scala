package blended.mgmt.ui.backend

import blended.updater.config.UpdateAction

import scala.collection.immutable
import blended.mgmt.ui.ConsoleSettings
import prickle.Pickle

trait ProfileUpdateAction {

  def addUpdateActions(containerId: String, updateActions: immutable.Seq[UpdateAction]): Unit

}


object ProfileUpdateAction {
  
  object DefaultAjax extends ProfileUpdateAction {

  def addUpdateActions(containerId: String, updateActions: immutable.Seq[UpdateAction]): Unit = {
    import blended.updater.config.json.PrickleProtocol._
    AjaxHelper.post(ConsoleSettings.containerDataUrl + "/" + containerId + "/udpate", Pickle.intoString(updateActions))
  }
}

  
}