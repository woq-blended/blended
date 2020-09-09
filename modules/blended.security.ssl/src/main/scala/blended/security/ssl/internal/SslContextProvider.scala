package blended.security.ssl.internal

import java.io.FileInputStream
import java.security.KeyStore

import blended.util.logging.Logger
import javax.net.ssl._

object SslContextProvider {
  private[ssl] val propTrustStore = "javax.net.ssl.trustStore"
  private[ssl] val propTrustStorePwd = "javax.net.ssl.trustStorePassword"
}

// scalastyle:off null
class SslContextProvider {

  import SslContextProvider.{propTrustStore, propTrustStorePwd}

  private[this] val log = Logger[SslContextProvider]

  private[this] lazy val trustManager : Array[TrustManager] = (
    Option(System.getProperty(propTrustStore)),
    Option(System.getProperty(propTrustStorePwd))
  ) match {
      case (Some(trustStore), Some(trustStorePassword)) =>
        log.debug("Configuring trust store from System Properties")

        val tks = KeyStore.getInstance(KeyStore.getDefaultType)
        tks.load(new FileInputStream(trustStore), trustStorePassword.toCharArray)

        val tmf : TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
        tmf.init(tks)

        tmf.getTrustManagers
      case (_, _) =>
        log.debug("Using default JVM trust manager")
        null
    }

  def serverContext(keystore : KeyStore, keyPass : Array[Char]) : SSLContext = {

    val keyManager : Array[KeyManager] = {
      val kmf = KeyManagerFactory.getInstance("SunX509")
      kmf.init(keystore, keyPass)
      kmf.getKeyManagers
    }

    val result = SSLContext.getInstance("TLSv1.2")
    result.init(keyManager, trustManager, null)

    result
  }

  lazy val clientContext : SSLContext = {
    val ctxt = SSLContext.getInstance("TLSv1.2")
    ctxt.init(null, trustManager, null)

    ctxt
  }
}
// scalastyle:on null
