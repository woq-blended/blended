package blended.activemq.brokerstarter.internal

import javax.net.ssl.SSLContext

import blended.akka.OSGIActorConfig

case class StartBroker(cfg : OSGIActorConfig, sslContext: Option[SSLContext])
case object StopBroker
