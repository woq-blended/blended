package blended.security.scep.internal

import java.net.URL
import java.security.cert.Certificate

import blended.security.ssl._
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.x500.X500Principal
import org.bouncycastle.asn1.DERPrintableString
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.jscep.client.verification.{CertificateVerifier, OptimisticCertificateVerifier}
import org.jscep.client.{Client, DefaultCallbackHandler}
import org.jscep.transport.response.Capabilities

import scala.collection.JavaConverters._
import scala.util.Try

case class ScepConfig(
  url : String,
  cnProvider : CommonNameProvider,
  profile: Option[String],
  keyLength : Int,
  csrSignAlgorithm : String,
  scepChallenge : String
)

class ScepCertificateProvider(cfg: ScepConfig) extends CertificateProvider {

  private[this] lazy val log = org.log4s.getLogger

  private[this] val subject = cfg.cnProvider.commonName()

  private[this] lazy val scepClient : Client = {
    val verifier : CertificateVerifier = new OptimisticCertificateVerifier()
    val handler : CallbackHandler = new DefaultCallbackHandler(verifier)

    new Client(new URL(cfg.url), handler)
  }

  private[this] lazy val caps : Capabilities = cfg.profile match {
    case None => scepClient.getCaCapabilities()
    case Some(p) => scepClient.getCaCapabilities(p)
  }

  override def refreshCertificate(existing: Option[ServerCertificate]): Try[ServerCertificate] = {
    log.info(s"Trying to refresh the server certificate via SCEP from [${cfg.url}]")
    existing match {
      case None =>
        log.info("Obtaining initial server certificate from SCEP server.")
        enroll(selfSignedCertificate().get)
      case Some(c) =>
        log.info("Refreshing certificate previously obtained from SCEP server.")
        enroll(c)
    }
  }

  private[this] def selfSignedCertificate() : Try[ServerCertificate] = {

    val selfSignedConfig = SelfSignedConfig(
      subject = subject,
      keyStrength = cfg.keyLength,
      sigAlg = caps.getStrongestSignatureAlgorithm(),
      validDays = 1
    )

    new SelfSignedCertificateProvider(selfSignedConfig).refreshCertificate(None)
  }

  private[this] def enroll(inCert : ServerCertificate): Try[ServerCertificate] = {

    val reqCert = inCert.chain.head

    log.info(s"Trying to obtain server certificate from SCEP server at [${cfg.url}] with existing certificate [${X509CertificateInfo(reqCert)}]" )

    val csrBuilder = new JcaPKCS10CertificationRequestBuilder(new X500Principal(subject), inCert.keyPair.getPublic())
    csrBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_challengePassword, new DERPrintableString(cfg.scepChallenge))

    // TODO addextensions ?

    val csrSignerBuilder = new JcaContentSignerBuilder(cfg.csrSignAlgorithm)
    val csrSigner = csrSignerBuilder.build(inCert.keyPair.getPrivate())
    val csr = csrBuilder.build(csrSigner)

    val response = scepClient.enrol(reqCert, inCert.keyPair.getPrivate(), csr)

    // TODO: Active wait is baaaad
    while(response.isPending()) {
      log.info(s"Waiting for PKI response from [${cfg.url}]")
      Thread.sleep(1000)
    }

    if (response.isFailure()) {
      val info = response.getFailInfo()
      log.error(s"Certificate provisioning failed: [$info]")
      sys.error(info.toString)
    } else {
      val store = response.getCertStore()
      val certs : List[Certificate] = store.getCertificates(null).asScala.toList

      log.info(s"Retrieved [${certs.length}] certificates from [${cfg.url}].")

      ServerCertificate.create(
        keyPair = inCert.keyPair,
        chain = certs
      )

    }
  }
}
