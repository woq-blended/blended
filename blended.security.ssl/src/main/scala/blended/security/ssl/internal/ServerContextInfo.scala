package blended.security.ssl.internal

import javax.net.ssl.SSLContext

class SSLContextInfo(name: String, sslContext : SSLContext) {

  val protocol : String = sslContext.getProtocol()

  val enabledProtocols : Seq[String] = sslContext.getDefaultSSLParameters().getProtocols().toSeq
  val enabledCypherSuites : Seq[String] = sslContext.getDefaultSSLParameters().getCipherSuites().toSeq

  override def toString: String = s"SSLContext(name=$name, protocol=$protocol," +
    s"enabledProtocols=${enabledProtocols.mkString(",")}," +
    s"enabledCyphers=\n${enabledCypherSuites.mkString("\n")}\n)"
}
