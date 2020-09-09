package blended.security.ssl

import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.{KeyPair, KeyPairGenerator, SecureRandom}

import blended.util.logging.Logger
import org.bouncycastle.cert.X509v3CertificateBuilder

import scala.util.Try

class SelfSignedCertificateProvider(cfg : SelfSignedConfig)
  extends CertificateProvider
  with CertificateRequestBuilder
  with CertificateSigner {

  private[this] val log = Logger[SelfSignedCertificateProvider]

  private def generateKeyPair() : KeyPair = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(cfg.keyStrength, new SecureRandom())
    kpg.genKeyPair()
  }

  override def refreshCertificate(existing : Option[CertificateHolder], cnProvider : CommonNameProvider) : Try[CertificateHolder] = Try {

    val oldCert = existing.map(_.chain.head)

    val requesterKeypair : KeyPair = existing match {
      case Some(c) => c.privateKey match {
        case None     => throw new NoPrivateKeyException("Existing certificate must have a private key to update")
        case Some(pk) => new KeyPair(c.publicKey, pk)
      }
      case None => generateKeyPair()
    }

    val serial = oldCert match {
      case Some(c) => c.getSerialNumber().add(BigInteger.ONE)
      case None    => BigInteger.ONE
    }

    val certBuilder : X509v3CertificateBuilder = hostCertificateRequest(
      cnProvider = cnProvider,
      serial = serial,
      validDays = cfg.validDays,
      keyPair = requesterKeypair
    ).get

    val cert : X509Certificate = sign(certBuilder, cfg.sigAlg, requesterKeypair.getPrivate()).get

    log.debug(s"Generated certificate [${X509CertificateInfo(cert)}]")
    CertificateHolder.create(requesterKeypair, List(cert)).get
  }
}
