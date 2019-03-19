package blended.security.ssl.internal

import blended.security.ssl.{ SslContextInfo => SslContextInfoTrait }
import javax.net.ssl.SSLContext

trait SslContextInfoMBean extends SslContextInfoTrait

class SslContextInfo(
  sslContext : SSLContext,
  cyphers: List[String]
) extends SslContextInfoMBean {

  override def getAllowedCypherSuites(): Array[String] = cyphers.toArray
  override def getProtocol(): String = sslContext.getProtocol()
  override def getEnabledProtocols(): Array[String] = sslContext.getDefaultSSLParameters().getProtocols()
  override def getEnabledCypherSuites(): Array[String] = sslContext.getDefaultSSLParameters().getCipherSuites()

}
