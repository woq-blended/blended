package blended.sslcontext.internal

import java.math.BigInteger
import java.security.{KeyPair, KeyPairGenerator}
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.security.auth.x500.X500Principal

import blended.sslcontext.CertificateProvider
import org.bouncycastle.asn1.x509.{KeyUsage, X509Extension}
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509v3CertificateBuilder}
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class SelfSignedCertificateProvider(
  subject: X500Principal,
  keyStrength : Int = 2048,
  signatureAlgorithm : String
) extends CertificateProvider {

  private def generateKeyPair() : KeyPair = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(keyStrength)
    kpg.genKeyPair()
  }

  override def provideCertificate(): (KeyPair, X509Certificate) = {

    val requesterKeypair = generateKeyPair()

    val requesterIssuer = subject
    val serial = BigInteger.ONE
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DATE, -1) // yesterday
    val notBefore = calendar.getTime()
    calendar.add(Calendar.DATE, 2) // tomorrow
    val notAfter = calendar.getTime()
    val requesterSubject = subject

    val certBuilder = new JcaX509v3CertificateBuilder(
      requesterIssuer, serial, notBefore, notAfter, requesterSubject, requesterKeypair.getPublic()
    )

    certBuilder.addExtension(X509Extension.keyUsage, false, new KeyUsage(KeyUsage.digitalSignature))

    val certSignerBuilder = new JcaContentSignerBuilder(signatureAlgorithm)
    val certSigner = certSignerBuilder.build(requesterKeypair.getPrivate())

    val certHolder = certBuilder.build(certSigner)

    val converter = new JcaX509CertificateConverter()
    (requesterKeypair, converter.getCertificate(certHolder))
  }
}
