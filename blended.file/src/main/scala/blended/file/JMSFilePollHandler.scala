package blended.file
import java.io.{File, FileInputStream}
import javax.jms.{ConnectionFactory, DeliveryMode, Message, Session}

import blended.jms.utils.{JMSMessageFactory, JMSSupport}

class JMSFilePollHandler(
  cf: ConnectionFactory,
  dest: String,
  deliveryMode: Int,
  priority: Int,
  ttl: Long,
  props: Map[String, String]
) extends FilePollHandler with JMSSupport with JMSMessageFactory[File] {

  override def createMessage(session: Session, content: File) : Message = {

    val buffer : Array[Byte] = new Array[Byte](4096)
    val is = new FileInputStream(content)

    val result = session.createBytesMessage()

    var cnt = 0
    do {
      cnt = is.read(buffer)
      if (cnt > 0) result.writeBytes(buffer, 0, cnt)
    } while(cnt >= 0)

    props.foreach{ case (k,v) =>
      result.setStringProperty(k, v)
    }

    result.setStringProperty("BlendedFileName", content.getName())
    result.setStringProperty("BledendFilePath", content.getAbsolutePath())

    result
  }

  override def processFile(f: File): Unit = {

    sendMessage(
      cf = cf,
      destName = dest,
      content = f,
      msgFactory = this,
      deliveryMode = deliveryMode,
      priority = priority,
      ttl = ttl
    ).foreach{t => throw t}
  }
}
