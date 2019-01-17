package blended.security.ssl.internal

import java.security.cert.X509Certificate
import java.security.{KeyPair, KeyPairGenerator, SecureRandom}

import blended.security.ssl._
import org.bouncycastle.cert.X509v3CertificateBuilder

import scala.util.{Success, Try}

object QuickSSLTests {

  def main(args: Array[String]) : Unit = {

    new QuickSSLTests().run()
  }
}

class QuickSSLTests
  extends CertificateRequestBuilder
  with CertificateSigner {

  private val keyStrength : Int = 2048
  private val sigAlg : String = "SHA256withRSA"
  private val validDays : Int = 365

  private val kpg : KeyPairGenerator = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048, new SecureRandom())

    kpg
  }

  val caKeys : KeyPair = kpg.generateKeyPair()

  private class HostnameCNProvider(hostname : String) extends CommonNameProvider {
    override def commonName(): Try[String] = Success(s"CN=$hostname")
  }

  private def createRootCertificate() : Try[ServerCertificate] = Try {

    val certReq : X509v3CertificateBuilder = hostCertificateRequest(
      cnProvider = new HostnameCNProvider("Root"),
      keyPair = caKeys
    ).get

    val cert : X509Certificate = sign(certReq, sigAlg, caKeys.getPrivate()).get

    ServerCertificate(caKeys, List(cert))
  }

  private def createHostCertificate(hostName: String, issuedBy : ServerCertificate) : Try[ServerCertificate] = Try {
    val certReq : X509v3CertificateBuilder = hostCertificateRequest(
      cnProvider = new HostnameCNProvider(hostName),
      keyPair = kpg.generateKeyPair(),
      issuedBy = Some(issuedBy)
    ).get

    val cert : X509Certificate = sign(certReq, sigAlg, caKeys.getPrivate()).get
    ServerCertificate.create(caKeys, cert :: issuedBy.chain).get
  }

  def run() : Unit = {

    val root : ServerCertificate = createRootCertificate().get
    println(root.dump)
    root.chain.head.verify(caKeys.getPublic())

    val server1 : ServerCertificate = createHostCertificate("server1", root).get
    println(server1.dump)
    server1.chain.head.verify(caKeys.getPublic())

  }
}
