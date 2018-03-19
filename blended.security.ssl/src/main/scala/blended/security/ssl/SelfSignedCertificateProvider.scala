package blended.security.ssl

import java.math.BigInteger
import java.security.{KeyPair, KeyPairGenerator}
import java.util.Calendar

import javax.security.auth.x500.X500Principal
import org.bouncycastle.asn1.x509._
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509v3CertificateBuilder}
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

import scala.util.Try

class SelfSignedCertificateProvider(cfg: SelfSignedConfig) extends CertificateProvider {

  private[this] val log = org.log4s.getLogger

  private def generateKeyPair(): KeyPair = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(cfg.keyStrength)
    kpg.genKeyPair()
  }

  override def refreshCertificate(existing: Option[ServerCertificate]): Try[ServerCertificate] = Try {

    log.info(s"Using ${cfg.commonNameProvider}")
    val oldCert = existing.map(_.chain.head)

    val requesterKeypair = generateKeyPair()

    val principal = new X500Principal(cfg.commonNameProvider.commonName().get)
    val requesterIssuer = principal
    val serial = oldCert match {
      case Some(c) => c.getSerialNumber().add(BigInteger.ONE)
      case None => BigInteger.ONE
    }
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DATE, -1)
    val notBefore = calendar.getTime()
    calendar.add(Calendar.DATE, 1 + cfg.validDays)
    val notAfter = calendar.getTime()
    val requesterSubject = principal

    val certBuilder = new JcaX509v3CertificateBuilder(
      requesterIssuer, serial, notBefore, notAfter, requesterSubject, requesterKeypair.getPublic()
    )

    if (cfg.commonNameProvider.alternativeNames().get.nonEmpty) {
      val altNames : Array[GeneralName] = cfg.commonNameProvider.alternativeNames().get.map { n=>
        log.debug(s"Adding alternative dns name [$n] to certificate.")
        new GeneralName(GeneralName.dNSName, n)
      }.toArray

      val names = new GeneralNames(altNames)
      log.debug(s"General Names : $names")

      certBuilder.addExtension(X509Extension.subjectAlternativeName, false, names)
    }

    certBuilder.addExtension(X509Extension.keyUsage, false, new KeyUsage(KeyUsage.digitalSignature))

    val certSignerBuilder = new JcaContentSignerBuilder(cfg.sigAlg)
    val certSigner = certSignerBuilder.build(requesterKeypair.getPrivate())

    val certHolder = certBuilder.build(certSigner)

    val converter = new JcaX509CertificateConverter()

    val cert = converter.getCertificate(certHolder)
    log.debug(s"Generated certificate ${X509CertificateInfo(cert)}")
    ServerCertificate(requesterKeypair, List(cert))
  }
}
