package blended.security.ssl

import java.io.File
import java.security.cert.X509Certificate
import java.security.{KeyPair, KeyPairGenerator, SecureRandom}

import blended.security.ssl.internal.JavaKeystore
import blended.testsupport.BlendedTestSupport
import org.bouncycastle.cert.X509v3CertificateBuilder

import scala.concurrent.duration._
import scala.util.{Success, Try}

case class HostnameCNProvider(hostname : String) extends CommonNameProvider {
  override def commonName() : Try[String] = Success(s"CN=$hostname")
}

trait SecurityTestSupport { this : CertificateRequestBuilder with CertificateSigner =>

  val keyStrength : Int = 2048
  val sigAlg : String = "SHA256withRSA"
  val validDays : Int = 20
  val millisPerDay : Long = 1.day.toMillis

  val kpg : KeyPairGenerator = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(keyStrength, new SecureRandom())

    kpg
  }

  val selfSignedCfg : CommonNameProvider => SelfSignedConfig = cnProvider => SelfSignedConfig(
    commonNameProvider = cnProvider,
    sigAlg = "SHA256withRSA",
    keyStrength = 2048,
    validDays = validDays
  )

  val jks : String => JavaKeystore = fileName => new JavaKeystore(
    keystoreFile(fileName),
    "storepass".toCharArray,
    Some("storepass".toCharArray)
  )

  def keystoreFile(name : String) : File = {
    val f = new File(BlendedTestSupport.projectTestOutput, name)
    if (f.exists()) {
      f.delete()
    }
    f
  }

  def createRootCertificate(cn : String = "root", validDays : Int = validDays) : Try[CertificateHolder] = Try {

    val cnProvider : CommonNameProvider = new HostnameCNProvider(cn)
    new SelfSignedCertificateProvider(selfSignedCfg(cnProvider).copy(validDays = validDays)).refreshCertificate(None, cnProvider).get
  }

  def createHostCertificate(hostName : String, issuedBy : CertificateHolder, validDays : Int = validDays) : Try[CertificateHolder] = Try {

    issuedBy.privateKey match {

      case None => throw new Exception("No signature key")
      case Some(k) =>

        val pair : KeyPair = kpg.generateKeyPair()

        val certReq : X509v3CertificateBuilder = hostCertificateRequest(
          cnProvider = HostnameCNProvider(hostName),
          keyPair = pair,
          validDays = validDays,
          issuedBy = Some(issuedBy)
        ).get

        val cert : X509Certificate = sign(certReq, sigAlg, k).get
        CertificateHolder.create(pair, cert :: issuedBy.chain).get
    }
  }
}
