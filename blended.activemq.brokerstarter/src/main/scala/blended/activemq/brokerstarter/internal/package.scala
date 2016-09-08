package blended.activemq.brokerstarter.internal

import blended.akka.OSGIActorConfig

case class StartBroker(cfg : OSGIActorConfig)
case object StopBroker
