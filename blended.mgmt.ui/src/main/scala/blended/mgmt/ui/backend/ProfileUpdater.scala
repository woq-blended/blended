package blended.mgmt.ui.backend

import blended.updater.config.UpdateAction

import scala.collection.immutable

trait ProfileUpdater {

  def addUpdateActions(containerId: String, updateActions: immutable.Seq[UpdateAction]): Unit

}


