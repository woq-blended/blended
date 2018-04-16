package blended.mgmt.ui

import scala.scalajs.js
import blended.mgmt.ui.util.Logger

object ConsoleSettings {
  def isSsl = {
    val prot = js.Dynamic.global.window.location.protocol.toString
    Logger[ConsoleSettings.type].debug("protocol: " + prot)
    prot == "https:"
  }
  def mgmtUrl =
    if (isSsl) "https://root:mysecret@mgmt:9996/mgmt"
    else "http://root:mysecret@mgmt:9995/mgmt"
  def containerDataUrl = mgmtUrl + "/container"
  def profilesUrl = mgmtUrl + "/profiles"
  def runtimeConfigsUrl = mgmtUrl + "/runtimeConfig"
  def overlayConfigUrl = mgmtUrl + "/overlayConfig"
  def rolloutProfile: String = mgmtUrl + "/rollout/profile"
}
