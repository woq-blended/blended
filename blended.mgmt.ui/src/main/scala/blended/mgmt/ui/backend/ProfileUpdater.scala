package blended.mgmt.ui.backend

import blended.updater.config.OverlaySet
import blended.updater.config.Profile
import blended.mgmt.ui.util.Logger
import scala.util.Success
import blended.updater.config.UpdateAction
import blended.mgmt.ui.ConsoleSettings
import org.scalajs.dom.ext.Ajax
import prickle.Pickle
import scala.collection.immutable

trait ProfileUpdater {

  def addUpdateActions(containerId: String, updateActions: immutable.Seq[UpdateAction]): Unit

}


