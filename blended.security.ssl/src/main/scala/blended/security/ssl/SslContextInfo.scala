package blended.security.ssl

trait SslContextInfo {

  val allowedCyphers : List[String]

  def getProtocol() : String
  def getEnabledProtocols() : Array[String]
  def getEnabledCypherSuites() : Array[String]

  def getInvalidCypherSuites() : Array[String] = {
    getEnabledCypherSuites().filter{ s => !allowedCyphers.contains(s) }
  }

  override def toString: String = s"SSLContextInfo(protocol=$getProtocol()," +
    s"enabledProtocols=${getEnabledProtocols().mkString(",")},\n" +
    s"enabledCyphers=${getEnabledCypherSuites().mkString(",\n")}\n" +
    s"invalidCyphers=${getInvalidCypherSuites().mkString(",\n")}\n)"
}
