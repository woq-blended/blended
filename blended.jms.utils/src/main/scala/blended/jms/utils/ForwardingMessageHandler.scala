package blended.jms.utils

import java.io.ByteArrayOutputStream
import javax.jms._

import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class ForwardingMessageHandler(cf: ConnectionFactory, destName: String, additionalHeader : Map[String, AnyRef] = Map.empty)
  extends JMSMessageHandler
  with JMSSupport
  with JMSMessageFactory {

  private[this] val log = LoggerFactory.getLogger(classOf[ForwardingMessageHandler])

  override def createMessage(session: Session, content: Option[Any]): Message = content match {
    case None => throw new Exception("No Message to forward")
    case Some(m) if m.isInstanceOf[Message] =>
      val original = m.asInstanceOf[Message]

      val result = original match {
        case tMsg : TextMessage =>
          val body = tMsg.getText()
          log.debug(s"Received text message [${tMsg.getJMSMessageID()}] of length [${body.length()}] : [${body.take(50)}]...")
          session.createTextMessage(tMsg.getText())
        case bMsg : BytesMessage =>
          log.debug(s"Received bytes message [${bMsg.getJMSMessageID()}] of length [${bMsg.getBodyLength()}]")
          val bytes = new Array[Byte](1024)
          val r = session.createBytesMessage()

          val bos = new ByteArrayOutputStream()

          var cnt = 0

          do {
            cnt = bMsg.readBytes(bytes)
            if (cnt > 0) bos.write(bytes, 0 , cnt)
          } while (cnt >= 0)

          r.writeBytes(bos.toByteArray())
          if (log.isDebugEnabled()) {
            log.debug(s"Forwarding bytes message of length [${bos.toByteArray().length}]")
          }
          r
        case pMsg =>
          log.warn(s"Message [${pMsg.getJMSMessageID()}] is of type [${pMsg.getClass().getName()}], forwarding as plain message")
          session.createMessage()
      }

      original.getPropertyNames().asScala.filter{ name =>
        !(name.toString().startsWith("JMS"))
      }.foreach { name =>
        result.setObjectProperty(name.toString(), original.getObjectProperty(name.toString()))
      }

      additionalHeader.foreach { case (k,v) => result.setObjectProperty(k,v) }

      result.setJMSCorrelationID(original.getJMSCorrelationID())
      result

    case Some(o) => throw new Exception(s"[$o] is not of type [${classOf[Message].getName()}]")
  }

  override def handleMessage(msg: Message): Option[Throwable] = {

    sendMessage(
      cf = cf,
      destName = destName,
      content = Some(msg),
      msgFactory = this,
      deliveryMode = msg.getJMSDeliveryMode(),
      priority = msg.getJMSPriority(),
      ttl = if (msg.getJMSExpiration() > 0) msg.getJMSExpiration() - System.currentTimeMillis() else 0
    )
  }
}
