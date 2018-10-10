package blended.jms.bridge.internal

import akka.actor.{Actor, Props}
import blended.jms.utils.IdAwareConnectionFactory
import blended.util.logging.Logger
import javax.jms.ConnectionFactory

private[bridge] case class BridgeControllerConfig(
  internalVendor : String,
  internalProvider : Option[String],
  internalConnectionFactory : ConnectionFactory,
  jmsProvider : List[BridgeProviderConfig],
  inbound : List[InboundConfig]
)

object BridgeController{

  case class AddConnectionFactory(cf : IdAwareConnectionFactory)
  case class RemoveConnectionFactory(cf : IdAwareConnectionFactory)

  def props(ctrlCfg: BridgeControllerConfig) : Props =
    Props(new BridgeController(ctrlCfg))
}

class BridgeController(ctrlCfg: BridgeControllerConfig) extends Actor {

  private[this] val log = Logger[BridgeController]

  override def receive: Receive = Actor.emptyBehavior
}
