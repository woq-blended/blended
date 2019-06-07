package blended.jms.utils

import blended.util.logging.Logger
import javax.jms.{BytesMessage, Message, Session, TextMessage}

import scala.collection.JavaConverters._

object CloningMessageFactory extends JMSMessageFactory[Message] {

  private[this] val log = Logger("blended.jms.utils.CloningMessageFactory")

  override def createMessage(session : Session, content : Message) : Message = {

    val result = content match {
      case tMsg : TextMessage =>
        session.createTextMessage(tMsg.getText)
      case bMsg : BytesMessage =>
        bMsg.reset()
        //scalastyle:off magic.number
        val bytes = new Array[Byte](1024)
        //scalastyle:on magic.number
        val r = session.createBytesMessage()

        var cnt = 0

        do {
          cnt = bMsg.readBytes(bytes)
          if (cnt > 0) r.writeBytes(bytes, 0, cnt)
        } while (cnt >= 0)
        r

      case pMsg =>
        log.warn(s"Message [${pMsg.getJMSMessageID}] is of type [${pMsg.getClass.getName}], forwarding as plain message")
        session.createMessage()
    }

    content.getPropertyNames.asScala.filter { name =>
      name == "JMSCorrelationID" || !name.toString.startsWith("JMS")
    }.foreach { name =>
      result.setObjectProperty(name.toString, content.getObjectProperty(name.toString))
    }

    result.setJMSCorrelationID(content.getJMSCorrelationID)
    result

  }
}
