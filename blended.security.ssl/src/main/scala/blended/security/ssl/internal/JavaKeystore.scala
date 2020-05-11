package blended.security.ssl.internal

import java.io.{File, FileInputStream, FileOutputStream}
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey, PublicKey}

import blended.security.ssl.{CertificateChange, CertificateHolder, InconsistentKeystoreException, MemoryKeystore}
import blended.util.logging.Logger
import scala.collection.JavaConverters._
import scala.util.Try

class JavaKeystore(
  store : File,
  storepass : Array[Char],
  keypass : Option[Array[Char]]
) {

  def keystore : File = store

  private val log : Logger = Logger[JavaKeystore]

  private[ssl] val storetype : String = keypass match {
    case None    => KeyStore.getDefaultType()
    case Some(_) => "PKCS12"
  }

  // Helper method to load a Java Keystore into memory, create the keystore on the fly if required
  def loadKeyStore() : Try[MemoryKeystore] = memoryKeystore(loadKeyStoreFromFile().get)

  def saveKeyStore(ms : MemoryKeystore) : Try[MemoryKeystore] = Try {

    val ks : KeyStore = loadKeyStoreFromFile().get

    ms.certificates.filter(_._2.change.changed).foreach {
      case (alias, cert) =>
        keypass match {
          case None =>
            ks.setCertificateEntry(alias, cert.chain.last)
          case Some(pwd) =>
            cert.privateKey match {
              case None    => throw new Exception(s"Certificate for [${cert.subjectPrincipal}] is missing the private key")
              case Some(k) => ks.setKeyEntry(alias, k, pwd, cert.chain.toArray)
            }
        }
    }

    saveKeyStoreToFile(ks).get
    MemoryKeystore(ms.certificates.mapValues(_.copy(change = CertificateChange.Unchanged)).toMap)
  }

  private[ssl] def loadKeyStoreFromFile() : Try[KeyStore] = Try {

    log.info(s"Initializing key store of type [$storetype] from file [${keystore.getAbsolutePath()}] ...")

    val ks = KeyStore.getInstance(storetype)

    if (keystore.exists()) {
      val fis = new FileInputStream(keystore)
      try {
        ks.load(fis, storepass)
      } finally {
        fis.close()
      }
    } else {
      log.info(s"Loading empty key store [${keystore.getAbsolutePath()}] ...")
      // scalastyle:off null
      ks.load(null, storepass)
      // scalastyle:on null
    }

    ks
  }

  private[ssl] def saveKeyStoreToFile(ks : KeyStore) : Try[KeyStore] = Try {
    val fos = new FileOutputStream(keystore)
    try {
      val certCount = ks.aliases().asScala.size
      log.info(s"Storing [$certCount] certificates to [$keystore]")
      ks.store(fos, storepass)
      log.info(s"Successfully written key store to [$keystore] with storePass [${new String(storepass)}]")
    } finally {
      fos.close()
    }

    ks
  }

  // Extract a single certificate from the underlying keystore
  // If a keypass is set, we will also extract the private key of the certificate
  private[ssl] def extractCertificate(ks : KeyStore, alias : String) : Try[CertificateHolder] = Try {

    val chain : List[X509Certificate] = Option(ks.getCertificateChain(alias)) match {
      case None => Option(ks.getCertificate(alias)) match {
        case None =>
          throw new Exception(s"Certificate for alias [$alias] not found.")
        case Some(c) => List(c.asInstanceOf[X509Certificate])
      }
      case Some(c) => c.toList.map(_.asInstanceOf[X509Certificate])
    }

    val pubKey : PublicKey = chain.head.getPublicKey()
    val privKey : Option[PrivateKey] = keypass.map { pwd =>
      ks.getKey(alias, pwd).asInstanceOf[PrivateKey]
    }

    CertificateHolder.create(publicKey = pubKey, privateKey = privKey, chain = chain).get
  }

  private[ssl] def memoryKeystore(ks : KeyStore) : Try[MemoryKeystore] = Try {

    val certs : Map[String, CertificateHolder] = ks.aliases().asScala.map { alias =>
      (alias, extractCertificate(ks, alias).get)
    }.toMap

    val result = MemoryKeystore(certs)

    if (result.consistent) {
      result
    } else {
      throw new InconsistentKeystoreException(s"KeyStore [${keystore.getAbsolutePath()}] is inconsistent.")
    }
  }
}
