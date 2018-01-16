package blended.scep.internal

import java.net.URL
import javax.security.auth.callback.CallbackHandler

import org.jscep.client.{Client, DefaultCallbackHandler}
import org.jscep.client.verification.{CertificateVerifier, ConsoleCertificateVerifier}
import org.jscep.transport.response.Capabilities
import org.slf4j.LoggerFactory

class ScepEnroller {

  private[this] val log = LoggerFactory.getLogger(classOf[ScepEnroller])

  def enroll(): Unit = {

    val url = new URL("http://localhost:8080/scep")

    val verifier : CertificateVerifier = new ConsoleCertificateVerifier()
    val handler : CallbackHandler = new DefaultCallbackHandler(verifier)

    val client : Client = new Client(url, handler)

    // TODO: insert optional profile String (ask SCEP admin)
    val caps : Capabilities = client.getCaCapabilities()

    log.info(caps.getStrongestCipher())
  }

}
