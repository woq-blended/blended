package de.woq.blended.itestsupport.jmx

import javax.management.remote.JMXServiceURL

trait JMXUrlProvider {
  def serviceUrl : JMXServiceURL
}

trait KarafJMXUrlProvider extends JMXUrlProvider {

  private var host = "localhost"
  private var port = 1099

  def withHost(h: String) = { host = h; this }
  def withHost(p: Int)    = { port = p; this }

  override def serviceUrl =
    new JMXServiceURL(s"service:jmx:rmi:///jndi/rmi://${host}:${port}/jmxrmi")
}