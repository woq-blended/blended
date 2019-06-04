package blended.mgmt.mock.clients

import de.tototec.cmdoption.CmdOption

case class Config(
  clientCount : Int = 200,
  url : String = "http://localhost:9995/mgmt/container",
  updateIntervalMsecMin : Long = 10000,
  updateIntervalMsecMax : Long = 30000,
  initialUpdateDelayMsec : Long = 2000
) {

  override def toString() : String = getClass().getSimpleName() +
    "(clientCount=" + clientCount +
    ",url=" + url +
    ",updateIntervalMsecMin=" + updateIntervalMsecMin +
    ",updateIntervalMsecMax=" + updateIntervalMsecMax +
    ",initialUpdateDelayMsec" + initialUpdateDelayMsec +
    ")"
}

object Config {

  class Factory() {

    private var config = Config()

    def get : Config = config

    @CmdOption(names = Array("--client-count", "-c"), args = Array("n"), description = "The number of clients to generate")
    def clientCount(count : Int) : Unit =
      config = config.copy(clientCount = count)

    @CmdOption(names = Array("--url", "-u"), args = Array("url"), description = "The URL of the management server")
    def url(url : String) : Unit =
      config = config.copy(url = url)

    @CmdOption(names = Array("--help", "-h"), description = "Print this help", isHelp = true)
    var showHelp : Boolean = false

    @CmdOption(names = Array("--update-interval-min"), args = Array("msec"),
      description = "The interval in milliseconds in which the mock containers should report itself to the management server")
    def updateIntervalMsecMin(i : Int) : Unit = config = config.copy(updateIntervalMsecMin = i)

    @CmdOption(names = Array("--update-interval-max"), args = Array("msec"),
      description = "The interval in milliseconds in which the mock containers should report itself to the management server")
    def updateIntervalMsecMax(i : Int) : Unit = config = config.copy(updateIntervalMsecMax = i)

    @CmdOption(names = Array("--inital-delay", "-d"), args = Array("msec"),
      description = "The delay in milliseconds, the mock containers should wait before thei start reporting to the management server")
    def initialUpdateDelayMsec(i : Int) : Unit = config = config.copy(initialUpdateDelayMsec = i)
  }

}
