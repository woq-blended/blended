package blended.itestsupport.jmx

import javax.management.remote.JMXServiceURL

trait JMXUrlProvider {
  def serviceUrl : JMXServiceURL
}

case class KarafJMXUrlProvider(host: String = "localhost", port: Integer = 1099) extends JMXUrlProvider {

  def withHost(h: String) = copy( host = h )
  def withHost(p: Int)    = copy( port = p )

  override def serviceUrl =
    new JMXServiceURL(s"service:jmx:rmi:///jndi/rmi://$host:$port/jmxrmi")
}