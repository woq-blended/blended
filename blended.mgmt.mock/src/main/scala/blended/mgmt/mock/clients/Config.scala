package blended.mgmt.mock.clients

import de.tototec.cmdoption.CmdOption

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

object Config {

  class Factory() {

    private var config = Config()

    def get: Config = config

    @CmdOption(names = Array("--client-count", "-c"), args = Array("n"), description = "The number of clients to generate")
    def clientCount(count: Int): Unit =
      config = config.copy(clientCount = count)

    @CmdOption(names = Array("--url", "-u"), args = Array("url"), description = "The URL of the managment server")
    def url(url: String): Unit =
      config = config.copy(url = url)

    @CmdOption(names = Array("--help", "-h"), description = "Print this help", isHelp = true)
    var showHelp: Boolean = false

    @CmdOption(names = Array("--update-interval", "-i"), args = Array("msec"),
      description = "The interval in milliseconds in which the mock containers should report itself to the management server")
    def updateIntervalMsec(i: Int): Unit = config = config.copy(updateIntervalMsec = i)

    @CmdOption(names = Array("--inital-delay", "-d"), args = Array("msec"),
      description = "The delay in milliseconds, the mock containers should wait before thei start reporting to the managment server")
    def initialUpdateDelayMsec(i: Int): Unit = config = config.copy(initialUpdateDelayMsec = i)
  }

}