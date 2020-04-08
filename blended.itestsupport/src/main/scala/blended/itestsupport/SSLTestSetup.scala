package blended.itestsupport

import java.net.Socket
import java.security.cert.X509Certificate

import javax.net.ssl._

object SSLTestSetup {

  def disableSSLClientVerification(): Unit = {

    val trustAllCerts = Array[TrustManager](new X509ExtendedTrustManager {

      override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
      override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String, socket: Socket): Unit = {}
      override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String, sslEngine: SSLEngine): Unit = {}

      override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
      override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String, socket: Socket): Unit = {}
      override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String, sslEngine: SSLEngine): Unit = {}

      override def getAcceptedIssuers: Array[X509Certificate] = Array.empty
    })

    val allHostsValid : HostnameVerifier = new HostnameVerifier() {
      override def verify(s: String, sslSession: SSLSession): Boolean = true
    }

    val sc : SSLContext = {
      val result = SSLContext.getInstance("SSL")
      result.init(null, trustAllCerts, new java.security.SecureRandom())
      SSLContext.setDefault(result)
      result
    }

    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
    HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
  }
}
