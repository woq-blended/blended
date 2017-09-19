package blended.mgmt.ui

object ConsoleSettings {
  val mgmtUrl = "http://root:mysecret@mgmt:9191/mgmt"
  val containerDataUrl = mgmtUrl + "/container"
  val profilesUrl = mgmtUrl + "/profiles"
  val runtimeConfigsUrl = mgmtUrl + "/runtimeConfig"
  val overlayConfigUrl = mgmtUrl + "/overlayConfig"
  val rolloutProfile: String = mgmtUrl + "/rollout/profile"
}
