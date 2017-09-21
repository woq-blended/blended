package blended.mgmt.mock.clients

case class Config(
    clientCount: Int = 1000,
    url: String = "http://mgmt:9191/mgmt/container",
    updateIntervalMsec: Long = 20000,
    initialUpdateDelayMsec: Long = 2000) {

  override def toString(): String = getClass().getSimpleName() +
    "(clientCount=" + clientCount +
    ",url=" + url +
    ",updateIntervalMsec=" + updateIntervalMsec +
    ",initialUpdateDelayMsec" + initialUpdateDelayMsec +
    ")"
}