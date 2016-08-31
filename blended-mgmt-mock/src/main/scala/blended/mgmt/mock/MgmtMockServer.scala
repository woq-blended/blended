package blended.mgmt.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.client.WireMock._

object MgmtMockServer {
  def main(args: Array[String]) : Unit = {

    val port = 9999

    val server = new WireMockServer(options().port(port))
    configureFor(port)
    server.start()

    stubFor(
      get(urlEqualTo("/foo"))
        .willReturn(aResponse().withStatus(200).withBodyFile("test.txt")
      )
    )

  }
}
