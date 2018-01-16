package blended.file
import java.io.{File, FileInputStream}
import javax.jms.{ConnectionFactory, Message, Session}

import blended.jms.utils.{JMSMessageFactory, JMSSupport}

class JMSFilePollHandler(
  cf: ConnectionFactory,
  dest: String,
  deliveryMode: Int,
  priority: Int,
  ttl: Long,
  props: Map[String, String]
) extends FilePollHandler with JMSSupport with JMSMessageFactory[(FileProcessCmd, File)] {

  override def createMessage(session: Session, content: (FileProcessCmd, File)) : Message = {

    val buffer : Array[Byte] = new Array[Byte](4096)
    val is = new FileInputStream(content._2)

    val result = session.createBytesMessage()

    try {
      var cnt = 0
      do {
        cnt = is.read(buffer)
        if (cnt > 0) result.writeBytes(buffer, 0, cnt)
      } while(cnt >= 0)
    } finally {
      is.close()
    }

    props.foreach{ case (k,v) =>
      result.setStringProperty(k, v)
    }

    result.setStringProperty("BlendedFileName", content._1.f.getName())
    result.setStringProperty("BlendedFilePath", content._1.f.getAbsolutePath())

    result
  }

  override def processFile(cmd: FileProcessCmd, f: File): Unit = {

    sendMessage(
      cf = cf,
      destName = dest,
      content = (cmd, f),
      msgFactory = this,
      deliveryMode = deliveryMode,
      priority = priority,
      ttl = ttl
    ).foreach{t => throw t}
  }
}
