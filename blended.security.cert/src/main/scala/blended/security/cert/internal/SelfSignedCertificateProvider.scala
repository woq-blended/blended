package blended.security.cert.internal

import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.{KeyPair, KeyPairGenerator}
import java.util.Calendar
import javax.security.auth.x500.X500Principal

import blended.security.cert.{CertificateProvider, ServerCertificate}
import org.bouncycastle.asn1.x509.{KeyUsage, X509Extension}
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509v3CertificateBuilder}
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class SelfSignedCertificateProvider(
  cfg : SelfSignedConfig
) extends CertificateProvider {

  private def generateKeyPair() : KeyPair = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(cfg.keyStrength)
    kpg.genKeyPair()
  }

  override def refreshCertificate(existing: Option[X509Certificate]): ServerCertificate = {

    val requesterKeypair = generateKeyPair()

    val principal = new X500Principal(cfg.subject)
    val requesterIssuer = principal
    val serial = existing match {
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

    certBuilder.addExtension(X509Extension.keyUsage, false, new KeyUsage(KeyUsage.digitalSignature))

    val certSignerBuilder = new JcaContentSignerBuilder(cfg.sigAlg)
    val certSigner = certSignerBuilder.build(requesterKeypair.getPrivate())

    val certHolder = certBuilder.build(certSigner)

    val converter = new JcaX509CertificateConverter()
    ServerCertificate(requesterKeypair, Array(converter.getCertificate(certHolder)))
  }
}
