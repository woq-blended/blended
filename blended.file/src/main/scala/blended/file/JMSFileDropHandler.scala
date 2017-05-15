package blended.file

import javax.jms.{ConnectionFactory, DeliveryMode, Message}

import akka.actor.ActorSystem
import blended.jms.utils.{CloningMessageFactory, JMSMessageHandler, JMSSupport}
import org.slf4j.LoggerFactory

case class JMSFileDropConfig(
  system: ActorSystem,
  cf : ConnectionFactory,
  dest: String,
  errDest: String,
  dirHeader: String,
  fileHeader: String,
  compressHeader: String,
  appendHeader: String,
  defaultDir : String
)

class JMSFileDropHandler(cfg: JMSFileDropConfig) extends JMSMessageHandler with JMSSupport {

  private[this] val log = LoggerFactory.getLogger(classOf[JMSFileDropHandler])

  private[this] def sendErrorMessage(msg: Message) = sendMessage[Message](
    cf = cfg.cf,
    destName = cfg.errDest,
    content = msg,
    msgFactory = new CloningMessageFactory(),
    deliveryMode = DeliveryMode.PERSISTENT
  )

  override def handleMessage(msg: Message): Option[Throwable] = {

    Option(msg.getStringProperty(cfg.fileHeader)) match {
      case None =>
        log.error(s"Message [${msg.getJMSMessageID()}] is missing the filename property [${cfg.fileHeader}]")
        sendErrorMessage(msg)

    }



    None
  }
}
