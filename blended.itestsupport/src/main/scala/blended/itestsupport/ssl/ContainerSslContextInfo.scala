package blended.itestsupport.ssl

import blended.jolokia.{JolokiaClient, JolokiaReadResult, MBeanSearchDef}
import blended.security.ssl.SslContextInfo
import spray.json.DefaultJsonProtocol._

import scala.util.Try

object ContainerSslContextInfo {

  def sslContextInfo(
    client: JolokiaClient,
    domain : String,
    name : String
  ) : Try[SslContextInfo] = Try {

    val mbean : JolokiaReadResult = client.search(MBeanSearchDef(
      jmxDomain = domain,
      searchProperties = Map(
        "type" -> "SslContext",
        "name" -> name
      )
    )).map { _.mbeanNames match {
      case h :: _ => client.read(h).get
      case Nil => throw new Exception(s"Ssl Context MBean for [$name] not found")
    }}.get

    val allowed : Array[String] =
      mbean.attributes.get("AllowedCypherSuites").get.convertTo[Array[String]]

    val protocol : String =
      mbean.attributes.get("Protocol").get.convertTo[String]

    val enabledProtocols: Array[String] =
      mbean.attributes.get("EnabledProtocols").get.convertTo[Array[String]]

    val enabledCypherSuites : Array[String] =
      mbean.attributes.get("EnabledCypherSuites").get.convertTo[Array[String]]

    new SslContextInfo() {
      override def getAllowedCypherSuites(): Array[String] = allowed
      override def getProtocol(): String = protocol
      override def getEnabledProtocols(): Array[String] = enabledProtocols
      override def getEnabledCypherSuites(): Array[String] = enabledCypherSuites
    }
  }
}
