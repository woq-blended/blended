package blended.mgmt.ui.backend

import blended.updater.config.RolloutProfile
import blended.mgmt.ui.ConsoleSettings
import prickle.Pickle

trait RolloutProfileAction {
  
  def rolloutProfile(rolloutProfile: RolloutProfile): Unit
  
}

object RolloutProfileAction {
  
  object DefaultAjax extends RolloutProfileAction {
 
      override def rolloutProfile(rolloutProfile: RolloutProfile): Unit = {
        import blended.updater.config.json.PrickleProtocol._
        AjaxHelper.post(ConsoleSettings.rolloutProfile, Pickle.intoString(rolloutProfile))
      }
 
  }
  
}