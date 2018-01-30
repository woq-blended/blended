package blended.scep.internal

import java.math.BigInteger
import java.net.URL
import java.security.cert.X509Certificate
import java.security.{KeyPair, KeyPairGenerator}
import java.util.Calendar
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.x500.X500Principal

import org.bouncycastle.asn1.DERPrintableString
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.{KeyUsage, X509Extension}
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509v3CertificateBuilder}
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.jscep.client.verification.{CertificateVerifier, ConsoleCertificateVerifier, OptimisticCertificateVerifier}
import org.jscep.client.{Client, DefaultCallbackHandler}
import org.jscep.transport.response.Capabilities
import org.slf4j.LoggerFactory

case class ScepConfig(
  url : String,
  profile: Option[String],
  requester : X500Principal,
  subject : X500Principal
)

class ScepEnroller(cfg: ScepConfig) {

  private[this] val log = LoggerFactory.getLogger(classOf[ScepEnroller])

  lazy val client : Client = {
    val verifier : CertificateVerifier = new OptimisticCertificateVerifier()
    val handler : CallbackHandler = new DefaultCallbackHandler(verifier)

    new Client(new URL(cfg.url), handler)
  }

  lazy val caps : Capabilities = cfg.profile match {
    case None => client.getCaCapabilities()
    case Some(p) => client.getCaCapabilities(p)
  }

  lazy val (requesterKeys, requesterCert) : (KeyPair, X509Certificate) = {
    val requesterKeypair = generateKeyPair(2048)

    val requesterIssuer = cfg.requester
    val serial = BigInteger.ONE
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DATE, -1) // yesterday
    val notBefore = calendar.getTime()
    calendar.add(Calendar.DATE, 2) // tomorrow
    val notAfter = calendar.getTime()
    val requesterSubject = cfg.subject

    val certBuilder = new JcaX509v3CertificateBuilder(
      requesterIssuer, serial, notBefore, notAfter, requesterSubject, requesterKeypair.getPublic()
    )

    certBuilder.addExtension(X509Extension.keyUsage, false, new KeyUsage(KeyUsage.digitalSignature))

    val certSignerBuilder = new JcaContentSignerBuilder(caps.getStrongestSignatureAlgorithm())
    val certSigner = certSignerBuilder.build(requesterKeypair.getPrivate())

    val certHolder = certBuilder.build(certSigner)

    val converter = new JcaX509CertificateConverter()
    (requesterKeypair, converter.getCertificate(certHolder))
  }

  private def generateKeyPair(strength: Int = 2048) : KeyPair = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(strength)
    kpg.genKeyPair()
  }

  def enroll(): Unit = {

    log.info("Enrolling entity")

    val csrBuilder = new JcaPKCS10CertificationRequestBuilder(cfg.subject, requesterKeys.getPublic())
    csrBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_challengePassword, new DERPrintableString("password"))

    // TODO addextensions ?

    val csrSignerBuilder = new JcaContentSignerBuilder("SHA1withRSA")
    val csrSigner = csrSignerBuilder.build(requesterKeys.getPrivate())
    val csr = csrBuilder.build(csrSigner)

    val response = client.enrol(requesterCert, requesterKeys.getPrivate(), csr)

    while(response.isPending()) {
      log.info("Waiting for PKI response")
      Thread.sleep(1000)
    }

    if (response.isFailure()) {
      val info = response.getFailInfo()
      log.info(s"Certificate provisioning failed: [$info]")
    } else {
      val store = response.getCertStore()
      val certs = store.getCertificates(null)

      log.info(s"Retrieved [${certs.size()}] certificates.")

    }
    val failed = response.isFailure()
    log.info(s"$failed")
  }
}
