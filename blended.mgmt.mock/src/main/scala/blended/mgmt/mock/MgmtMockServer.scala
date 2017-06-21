package blended.mgmt.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.{ HttpHeader, HttpHeaders }
import blended.updater.config.ContainerInfo

object MgmtMockServer {

  import MockObjects._

  def main(args: Array[String]): Unit = {

    def response(body: String) = aResponse()
      .withStatus(200)
      .withHeaders(HttpHeaders.noHeaders().plus(new HttpHeader("Access-Control-Allow-Origin", "*")))
      .withBody(body)

    val port = 9191

    // First start the Server
    val server = new WireMockServer(options().port(port))
    server.start()

    // The we use the Wiremockmock client API to inject the stubs into the running server
    configureFor(port) // Important :: Point to the same port as the server - this is the client target port

    stubFor(
      get(urlEqualTo("/empty")).willReturn(response(containerList(emptyEnv)))
    )

    stubFor(
      get(urlEqualTo("/minimal")).willReturn(response(containerList(minimalEnv)))
    )

    stubFor(
      get(urlEqualTo("/mgmt/container")).willReturn(response(remoteContainerStateList(mediumEnv)))
    )

    stubFor(
      get(urlEqualTo("/mgmt/profiles")).willReturn(response(profilesList(validProfiles)))
    )

  }
}
