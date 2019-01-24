package blended.security.ssl

import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.{KeyPair, PrivateKey}
import java.util.Calendar

import blended.util.logging.Logger
import javax.security.auth.x500.X500Principal
import org.bouncycastle.asn1.x509.{Extension, GeneralName, GeneralNames, KeyUsage}
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509v3CertificateBuilder}
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

import scala.util.Try

trait CertificateRequestBuilder {

  private val log : Logger = Logger[CertificateRequestBuilder]

  def hostCertificateRequest(
    cnProvider : CommonNameProvider,
    keyPair : KeyPair,
    serial : BigInteger = new BigInteger("1"),
    validDays : Int = 365,
    issuedBy : Option[CertificateHolder] = None
  ) : Try[X509v3CertificateBuilder] = Try {

    val principal : X500Principal = new X500Principal(cnProvider.commonName().get)
    val signer : X500Principal = issuedBy match {
      case None => principal
      case Some(c) => c.chain.head.getSubjectX500Principal()
    }

    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DATE, -1)
    val notBefore = calendar.getTime()
    calendar.add(Calendar.DATE, 1 + validDays)
    val notAfter = calendar.getTime()

    val certBuilder : X509v3CertificateBuilder = new JcaX509v3CertificateBuilder(
      signer, serial, notBefore, notAfter, principal, keyPair.getPublic()
    )

    if (cnProvider.alternativeNames().get.nonEmpty) {
      val altNames : Array[GeneralName] = cnProvider.alternativeNames().get.map { n=>
        log.debug(s"Adding alternative dns name [$n] to certificate.")
        new GeneralName(GeneralName.dNSName, n)
      }.toArray

      val names = new GeneralNames(altNames)
      log.debug(s"General Names : $names")

      certBuilder.addExtension(Extension.subjectAlternativeName, false, names)
    }

    certBuilder.addExtension(Extension.keyUsage, false, new KeyUsage(KeyUsage.digitalSignature))

    certBuilder
  }
}

trait CertificateSigner {

  def sign(
    certReq : X509v3CertificateBuilder,
    sigAlg : String,
    signKey : PrivateKey
  ) : Try[X509Certificate] = Try {

    val certSignerBuilder = new JcaContentSignerBuilder(sigAlg)
    val certSigner = certSignerBuilder.build(signKey)
    val certHolder = certReq.build(certSigner)
    val converter : JcaX509CertificateConverter = new JcaX509CertificateConverter()
    converter.getCertificate(certHolder)
  }
}
