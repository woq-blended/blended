package blended.testsupport

import java.net.ServerSocket

object BlendedTestSupport {

  val projectTestOutput : String  = System.getProperty("projectTestOutput", "")

  def freePort : Int = {
    val socket = new ServerSocket(0)
    val freePort = socket.getLocalPort()
    socket.close()

    freePort
  }

}
