package blended.jms.bridge.internal

import akka.actor.Actor
import blended.util.logging.Logger

class BridgeController extends Actor {

  private[this] val log = Logger[BridgeController]

  override def receive: Receive = Actor.emptyBehavior
}
