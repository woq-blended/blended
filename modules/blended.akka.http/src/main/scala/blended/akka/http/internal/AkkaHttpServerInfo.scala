package blended.akka.http.internal

case class AkkaHttpServerInfo(
  host : Option[String] = None,
  port : Option[Int] = None,
  sslHost : Option[String] = None,
  sslPort : Option[Int] = None,
  routes : Array[String] = Array.empty
) {
  def clearPlainAddress() : AkkaHttpServerInfo = copy(host = None, port = None)
  def clearSslAddress() : AkkaHttpServerInfo = copy(sslHost = None, sslPort = None)
  def withHost(h : String) : AkkaHttpServerInfo = copy(host = Some(h))
  def withPort(p : Int) : AkkaHttpServerInfo = copy(port = Some(p))
  def withSslHost(h : String) : AkkaHttpServerInfo = copy(sslHost = Some(h))
  def withSslPort(p : Int) : AkkaHttpServerInfo = copy(sslPort = Some(p))
  def addRoute(r : String) : AkkaHttpServerInfo = copy(routes = (r :: routes.toList).distinct.sorted.toArray)
  def removeRoute(r : String) : AkkaHttpServerInfo = copy(routes = routes.filter(_ != r))

  override def toString() : String = {
    s"${getClass().getSimpleName()}(host=${host.getOrElse("")},port=${port.getOrElse("")}, sslHost=${sslHost.getOrElse("")}, sslPort=${sslPort.getOrElse("")}, routes=${routes.mkString("[", ",", "]")})"
  }
}
