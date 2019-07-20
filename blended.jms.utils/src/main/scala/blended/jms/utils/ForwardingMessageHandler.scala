package blended.jms.utils

import javax.jms._

@Deprecated
class ForwardingMessageHandler(cf : ConnectionFactory, destName : String, additionalHeader : Map[String, AnyRef] = Map.empty)
  extends JMSMessageHandler
  with JMSSupport
  with JMSMessageFactory[Message] {

  override def createMessage(session : Session, content : Message) : Message = {

    val result = CloningMessageFactory.createMessage(session, content)
    additionalHeader.foreach { case (k, v) => result.setObjectProperty(k, v) }
    result
  }

  override def handleMessage(msg : Message) : Option[Throwable] = {

    sendMessage(
      cf = cf,
      destName = destName,
      content = msg,
      msgFactory = this,
      deliveryMode = msg.getJMSDeliveryMode(),
      priority = msg.getJMSPriority(),
      ttl = if (msg.getJMSExpiration() > 0) msg.getJMSExpiration() - System.currentTimeMillis() else 0
    )
  }
}
