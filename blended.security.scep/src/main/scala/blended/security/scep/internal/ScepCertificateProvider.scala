package blended.security.scep.internal

import java.io.StringWriter
import java.net.URL
import java.security.cert.Certificate

import blended.security.ssl.{MemoryKeystore, _}
import blended.util.logging.Logger
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.x500.X500Principal
import org.bouncycastle.asn1.DERPrintableString
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.{Extension, ExtensionsGenerator, GeneralName, GeneralNames}
import org.bouncycastle.openssl.PEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.jscep.client.verification.{CertificateVerifier, OptimisticCertificateVerifier}
import org.jscep.client.{Client, DefaultCallbackHandler}
import org.jscep.transport.response.Capabilities

import scala.collection.JavaConverters._
import scala.util.Try

case class ScepConfig(
  url : String,
  profile : Option[String],
  keyLength : Int,
  csrSignAlgorithm : String,
  scepChallenge : String
)

class ScepCertificateProvider(cfg : ScepConfig)
  extends CertificateRequestBuilder
  with CertificateProvider {

  private[this] lazy val log = Logger[ScepCertificateProvider]

  private[this] lazy val scepClient : Client = {
    val verifier : CertificateVerifier = new OptimisticCertificateVerifier()
    val handler : CallbackHandler = new DefaultCallbackHandler(verifier)

    new Client(new URL(cfg.url), handler)
  }

  private[this] lazy val caps : Capabilities = cfg.profile match {
    case None    => scepClient.getCaCapabilities()
    case Some(p) => scepClient.getCaCapabilities(p)
  }

  override def rootCertificates() : Try[Option[MemoryKeystore]] = Try {
    val certs : List[Certificate] =
      scepClient.getCaCertificate().getCertificates(null).asScala.toList

    val ms : MemoryKeystore = MemoryKeystore(Map("ca" -> CertificateHolder.create(certs).get))

    Some(ms)
  }

  override def refreshCertificate(existing : Option[CertificateHolder], cnProvider : CommonNameProvider) : Try[CertificateHolder] = {
    log.info(s"Trying to refresh the server certificate via SCEP from [${cfg.url}]")
    existing match {
      case None =>
        log.info("Obtaining initial server certificate from SCEP server.")
        enroll(None, cnProvider)
      case Some(c) =>
        log.info("Refreshing certificate previously obtained from SCEP server.")
        enroll(Some(c), cnProvider)
    }
  }

  private[this] def selfSignedCertificate(cnProvider : CommonNameProvider) : Try[CertificateHolder] = {

    val selfSignedConfig = SelfSignedConfig(
      commonNameProvider = cnProvider,
      keyStrength = cfg.keyLength,
      sigAlg = caps.getStrongestSignatureAlgorithm(),
      validDays = 1
    )

    new SelfSignedCertificateProvider(selfSignedConfig).refreshCertificate(None, cnProvider)
  }

  private def dumpCsr(csr : PKCS10CertificationRequest) : String = {

    val str = new StringWriter()
    val pemWriter = new PEMWriter(str)
    pemWriter.writeObject(csr)
    pemWriter.close()
    str.close()
    str.toString()
  }

  private[this] def enroll(
    inCert : Option[CertificateHolder],
    cnProvider : CommonNameProvider
  ) : Try[CertificateHolder] = Try {

    val reqCert : CertificateHolder = inCert match {
      case None =>
        log.info(s"Requesting initial certificate from SCEP server at [${cfg.url}].")
        val selfSigned = selfSignedCertificate(cnProvider).get
        selfSigned
      case Some(c) =>
        log.info(s"Refreshing certificate from SCEP server at [${cfg.url}].")
        c
    }

    val privKey = reqCert.privateKey.get

    val csrBuilder = new JcaPKCS10CertificationRequestBuilder(new X500Principal(cnProvider.commonName().get), reqCert.publicKey)
    csrBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_challengePassword, new DERPrintableString(cfg.scepChallenge))

    if (cnProvider.alternativeNames().get.nonEmpty) {
      val altNames : Array[GeneralName] = cnProvider.alternativeNames().get.map { n =>
        log.info(s"Adding alternative dns name [$n] to SCEP certificate request.")
        new GeneralName(GeneralName.dNSName, n)
      }.toArray

      val names = new GeneralNames(altNames)
      val extGen = new ExtensionsGenerator()
      val sanExt = extGen.addExtension(Extension.subjectAlternativeName, false, names)

      csrBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate())
    }

    val csrSignerBuilder = new JcaContentSignerBuilder(cfg.csrSignAlgorithm)
    val csrSigner = csrSignerBuilder.build(privKey)
    val csr = csrBuilder.build(csrSigner)

    log.debug(s"csr: ${dumpCsr(csr)}")

    val response = scepClient.enrol(reqCert.chain.head, privKey, csr)

    // TODO: Active wait is baaaad
    while (response.isPending()) {
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

      CertificateHolder.create(
        publicKey = reqCert.publicKey,
        privateKey = Some(privKey),
        chain = certs
      ).get
    }
  }
}
