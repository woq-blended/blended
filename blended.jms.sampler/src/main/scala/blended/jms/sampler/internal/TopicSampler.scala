package blended.jms.sampler.internal

import java.io.{ByteArrayOutputStream, File}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import javax.jms._

import blended.util.FileHelper
import org.slf4j.LoggerFactory

class TopicSampler(dir: File, cf: ConnectionFactory, destName: String, encoding: String) {

  private[this] val log = LoggerFactory.getLogger(classOf[TopicSampler])
  private[this] val df = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS")
  private[this] var count = new AtomicLong(0)

  private[this] var con : Option[Connection] = None
  private[this] var session : Option[Session] = None
  private[this] var consumer : Option[MessageConsumer] = None

  def initSampler() : Unit = {
    con = Some(cf.createConnection())
    con.foreach(c => session = Some(c.createSession(false, Session.AUTO_ACKNOWLEDGE)))
    log.info(s"Creating sampling consumer on topic [$destName]")
    session.foreach { s =>
      val t = s.createTopic(destName)
      consumer = Some(s.createConsumer(t))
    }
  }

  def sample() : Unit = {

    def writeMsg(bytes: Array[Byte]) : Unit = {
      try {
        val fileName = s"$destName-${df.format(new Date())}-${count.incrementAndGet()}"
        val file = new File(dir, fileName)
        FileHelper.writeFile(file, bytes)
        log.info(s"Written [${bytes.length}] bytes to [${file.getAbsolutePath()}]")
      }
    }

    consumer match {
      case None => log.warn(s"Consumer for topic [$destName] is not initialised.")
      case Some(c) =>
        Option(c.receive(100l)).foreach {
          case bMsg : BytesMessage =>
            val bytes = new Array[Byte](8192)
            val bos = new ByteArrayOutputStream()

            var bCnt = 0

            do {
              bCnt = bMsg.readBytes(bytes)
              bos.write(bytes,0,bCnt)
            } while(bCnt > 0)

            bos.flush()
            bos.close()

            writeMsg(bos.toByteArray())

          case tMsg : TextMessage =>
            writeMsg(tMsg.getText().getBytes(encoding))

          case m : Message => log.info(s"Sampler does not process messages of type [${m.getClass().getSimpleName()}]")
        }
    }
  }

  def closeSampler() : Unit = {
    {
      session.foreach(_.close())
    }

    {
      con.foreach(_.close())
    }
  }
}
