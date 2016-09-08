package blended.mgmt.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.client.WireMock._

object MgmtMockServer {

  import MockObjects._

  def main(args: Array[String]) : Unit = {

    val port = 9999

    // First start the Server
    val server = new WireMockServer(options().port(port))
    server.start()

    // The we use the Wiremockmock client API to inject the stubs into the running server
    configureFor(port)   // Important :: Point to the same port as the server - this is the client target port

    stubFor(
      get(urlEqualTo("/container"))
        .willReturn(aResponse().withStatus(200).withBody(containerList(minimalEnv))
      )
    )

  }
}
