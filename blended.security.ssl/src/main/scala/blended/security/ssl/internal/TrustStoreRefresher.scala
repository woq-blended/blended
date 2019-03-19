package blended.security.ssl.internal

import java.io.File
import java.security.cert.X509Certificate
import java.util.UUID

import blended.security.ssl.{CertificateHolder, MemoryKeystore}
import blended.security.ssl.internal.SslContextProvider.{propTrustStore, propTrustStorePwd}
import blended.util.logging.Logger
import javax.security.auth.x500.X500Principal

import scala.util.Try

class TrustStoreRefresher(ms : MemoryKeystore) {

  private val log : Logger = Logger[TrustStoreRefresher]

  def refreshTruststore() : Try[Option[MemoryKeystore]] = Try {

    (
      Option(System.getProperty(propTrustStore)),
      Option(System.getProperty(propTrustStorePwd))
    ) match {
      case (Some(store), Some(pwd)) =>
        val f = new File(store)
        log.info(s"Reading trust store certificates from [${f.getAbsolutePath()}]")
        val jks = new JavaKeystore(new File(store), pwd.toCharArray(), None)

        val updated : MemoryKeystore = updateRoots(jks.loadKeyStore().get, ms).get
        Some(jks.saveKeyStore(updated).get)

      case _ => None
    }
  }

  private def updateRoot(trusted: MemoryKeystore, cert: CertificateHolder) : Try[MemoryKeystore] = Try {

    val root : X509Certificate = cert.chain.last
    val rootCn : X500Principal = root.getSubjectX500Principal()

    log.info(s"Checking trusted certificate for [$rootCn]")

    trusted.findByPrincipal(rootCn) match {
      case None =>
        log.info(s"Updating trust store with cerificate for [$rootCn]")
        val alias : String = if (trusted.certificates.isDefinedAt(rootCn.toString())) {
          UUID.randomUUID().toString()
        } else {
          rootCn.toString()
        }

        trusted.update(alias, CertificateHolder.create(root)).get
      case Some(_) =>
        log.info(s"Certificate for [$rootCn] already exists in trust store.")
        trusted
    }
  }

  private def updateRoots(trusted : MemoryKeystore, keystore: MemoryKeystore) : Try[MemoryKeystore] = Try {
    keystore.certificates.foldLeft(trusted){ case (s, (alias, c)) => updateRoot(s, c).get }
  }
}
