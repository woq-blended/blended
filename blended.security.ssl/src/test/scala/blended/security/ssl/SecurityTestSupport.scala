package blended.security.ssl

import java.security.cert.X509Certificate
import java.security.{KeyPair, KeyPairGenerator, SecureRandom}

import org.bouncycastle.cert.X509v3CertificateBuilder

import scala.util.{Success, Try}

case class HostnameCNProvider(hostname : String) extends CommonNameProvider {
  override def commonName(): Try[String] = Success(s"CN=$hostname")
}

trait SecurityTestSupport { this : CertificateRequestBuilder with CertificateSigner =>

  val keyStrength : Int = 2048
  val sigAlg : String = "SHA256withRSA"
  val validDays : Int = 365

  val kpg : KeyPairGenerator = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(keyStrength, new SecureRandom())

    kpg
  }

  def createRootCertificate() : Try[CertificateHolder] = Try {

    val caKeys = kpg.generateKeyPair()

    val certReq : X509v3CertificateBuilder = hostCertificateRequest(
      cnProvider = new HostnameCNProvider("Root"),
      keyPair = caKeys,
      issuedBy = None
    ).get

    val cert : X509Certificate = sign(certReq, sigAlg, caKeys.getPrivate()).get

    CertificateHolder.create(caKeys, List(cert)).get
  }

  def createHostCertificate(hostName: String, issuedBy : CertificateHolder) : Try[CertificateHolder] = Try {

    issuedBy.privateKey match {

      case None => throw new Exception("No signature key")
      case Some(k) =>

        val pair : KeyPair = kpg.generateKeyPair()

        val certReq: X509v3CertificateBuilder = hostCertificateRequest(
          cnProvider = HostnameCNProvider(hostName),
          keyPair = pair,
          issuedBy = Some(issuedBy)
        ).get

        val cert: X509Certificate = sign(certReq, sigAlg, k).get
        CertificateHolder.create(pair, cert :: issuedBy.chain).get
    }
  }
}
