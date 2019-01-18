package blended.security.ssl.internal

import java.io.{File, FileInputStream}
import java.security.KeyStore

import blended.util.logging.Logger

import scala.util.{Failure, Try}

object JavaKeystore {

  private val log : Logger = Logger[JavaKeystore.type]

  // Helper method to load a Java Keystore into memory, create the keystore on the fly if required
  def loadKeyStore(
    keyStore: File,
    storePass: Array[Char],
    keyPass: Array[Char]

  ): Try[ServerKeyStore] = {

    log.info(s"Initializing key store [${keyStore.getAbsolutePath()}] for server ...")

    val ks = KeyStore.getInstance("PKCS12")

    if (keyStore.exists()) {
      val fis = new FileInputStream(keyStore)
      try {
        ks.load(fis, storePass)
      } finally {
        fis.close()
      }
    } else {
      log.info(s"Creating empty key store [${keyStore.getAbsolutePath()}] ...")
      ks.load(null, storePass)
    }

    Failure(new Exception("Boom"))
  }
}
