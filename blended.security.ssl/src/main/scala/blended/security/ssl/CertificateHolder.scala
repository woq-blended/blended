package blended.security.ssl

import java.security.{KeyPair, Principal, PrivateKey, PublicKey}
import java.security.cert.{Certificate, X509Certificate}

import javax.security.auth.x500.X500Principal

import scala.util.Try

/**
  * A simple case class to hold a certificate chain in memory. A certificate chain
  * in this class is always sorted, so that the ultimate root certificate is at the
  * end of the chain and the certificate is always at the head of the chain.
  *
  * The keys always belong to the certificate at the head of the chain. The private
  * key is optional - In that case the certificate can not be used as a server side
  * certificate.
  *
  * The constructor is private, so that instances of the class can only be created
  * with the factory method(s) in the companion object. These methods will create the
  * sorted chain verify the signatures of each certificate within the chain.
  */
case class CertificateHolder (
  publicKey : PublicKey,
  privateKey : Option[PrivateKey],
  chain: List[X509Certificate],
  changed : Boolean = false
) {

  val subjectPrincipal : Option[X500Principal] = chain.headOption.map(_.getIssuerX500Principal())

  // retrieve the keyPair of the certificate
  val keyPair : Option[KeyPair] = privateKey.map(pk => new KeyPair(publicKey, pk))

  // A simple String representation of the certificate chain
  override def toString: String = chain.map { c=>
    X509CertificateInfo(c).toString
  }.mkString("\n", "\n", "")

  // A complete dump of the certificate chain as a String
  def dump : String = {
    chain.map { c => c.toString() }.mkString("\n" + "*" * 30 , "\n\n--- Signed by ---\n\n", "*" * 30)
  }
}

object CertificateHolder {

  def create(publicKey: PublicKey, chain : List[Certificate]) : Try[CertificateHolder] =
    create(publicKey, None, chain)

  def create(keyPair : KeyPair, chain: List[Certificate]) : Try[CertificateHolder] =
    create(keyPair.getPublic(), Some(keyPair.getPrivate()), chain)

  def create(publicKey : PublicKey, privateKey: Option[PrivateKey], chain : List[Certificate]) : Try[CertificateHolder] = Try {

    val sortedChain: List[X509Certificate] = {

      chain.map(_.asInstanceOf[X509Certificate]) match {
        // chain must not be empty
        case Nil =>
          throw new EmptyCertificateChainException

        case certs => certs.find { c => c.getSubjectDN().equals(c.getIssuerDN()) } match {
          // chain must have a root certificate
          case None =>
            throw new MissingRootCertificateException

          case Some(root) => {
            // The root must have signed itself correctly
            root.verify(root.getPublicKey())
            // We kick off the sort with the root certificate as a starting point
            sort(certs.filter(c => !c.equals(root)))(root :: Nil).get
          }
        }
      }
    }

    CertificateHolder(publicKey, privateKey, sortedChain)
  }

  // A test that yields true if and only if the certificate is not self signed AND was signed by
  // the given principal
  private def signedBy(issuer: Principal): (X509Certificate => Boolean) = c =>
    !c.getIssuerDN().equals(c.getSubjectDN()) && c.getIssuerDN().equals(issuer)

  // Helper functio to sort the certificates of a given chain so that any certificate in the chain is
  // signed by it's successor. This implies that the root certificate is always the last element in the list
  private def sort(remaining: List[X509Certificate])(sorted: List[X509Certificate]): Try[List[X509Certificate]] = Try {
    remaining match {
      // We have visited and sorted all certificates
      case Nil => sorted
      // for the head of the already sorted certificates we look for the one that has been signed
      // by it and prepend it to the list of sorted certificates
      case rest =>
        rest.find(signedBy(sorted.head.getSubjectDN())) match {
          case None =>
            throw new CertificateChainException(s"No signed certificate found for certificate [${X509CertificateInfo(sorted.head)}]")

          case Some(next) =>
            if (sorted.contains(next)) {
              throw new CertificateChainException("Certificate chain must not contain circular references")
            } else {
              // the certificate we have found must be signed by the first element in the sorted list
              // correctly
              next.verify(sorted.head.getPublicKey())
              sort(remaining.filter(c => !c.equals(next)))(next :: sorted).get
            }
        }
    }
  }
}

