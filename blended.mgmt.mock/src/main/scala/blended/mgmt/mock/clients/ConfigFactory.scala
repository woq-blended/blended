package blended.mgmt.mock.clients

import de.tototec.cmdoption.CmdOption

class ConfigFactory {

  private var config = Config()

  def get: Config = config

  @CmdOption(names = Array("--client-count", "-c"), args = Array("n"), description = "The number of clients to generate")
  def clientCount(count: Int): Unit =
    config = config.copy(clientCount = count)

  @CmdOption(names = Array("--url", "-u"), args = Array("url"), description = "The URL of the managment server")
  def url(url: String): Unit =
    config = config.copy(url = url)

  @CmdOption(names = Array("--help", "-h"), description = "Print this help")
  var showHelp: Boolean = false

}