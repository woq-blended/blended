package blended.jms.bridge.internal

import akka.actor.Actor
import blended.util.logging.Logger

case class StreamControllerConfig(

)

class StreamController extends Actor {

  private[this] val log = Logger[StreamController]

  override def receive: Receive = Actor.emptyBehavior
}
