package blended.file

import java.io.ByteArrayOutputStream
import javax.jms._

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
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
  charsetHeader: String,
  defaultDir : String
)

class JMSFileDropHandler(cfg: JMSFileDropConfig) extends JMSMessageHandler with JMSSupport {

  class JMSFileDropActor extends Actor with ActorLogging {

    private[this] def sendErrorMessage(msg: Message) = sendMessage[Message](
        cf = cfg.cf,
        destName = cfg.errDest,
        content = msg,
        msgFactory = new CloningMessageFactory(),
        deliveryMode = DeliveryMode.PERSISTENT
      )

    override def receive: Receive = {
      case msg : Message =>
        Option(msg.getStringProperty(cfg.fileHeader)) match {
          case None =>
            log.error(s"Message [${msg.getJMSMessageID()}] is missing the filename property [${cfg.fileHeader}]")
            sendErrorMessage(msg)

        case Some(fileName) =>

          (msg match {
            case tMsg : TextMessage =>
              val charSet = Option(tMsg.getStringProperty(cfg.charsetHeader)).getOrElse("UTF-8")
              log.info(s"Using charset [$charSet] to file drop text message [${tMsg.getJMSMessageID()}]")
              Some(tMsg.getText().getBytes(charSet))
            case bMsg: BytesMessage =>
              val buffer = new Array[Byte](1024)
              val os = new ByteArrayOutputStream()
              var count = 0
              do {
                count = bMsg.readBytes(buffer)
                if (count > 0) os.write(buffer, 0, count)
              } while(count >= 0)
              os.close()
              Some(os.toByteArray())

            case msg =>
              log.error(s"Dropping files unsupported for msg [${msg.getJMSMessageID()}] of type [${msg.getClass().getName()}]")
              sendErrorMessage(msg)
              None
          }).foreach{ content =>
            val cmd = FileDropCommand(
              content = "Hallo Andreas".getBytes(),
              directory = Option(msg.getStringProperty(cfg.dirHeader)).getOrElse(cfg.defaultDir),
              fileName = fileName,
              compressed = Option(msg.getBooleanProperty(cfg.compressHeader)).getOrElse(false),
              append = Option(msg.getBooleanProperty(cfg.appendHeader)).getOrElse(false),
              timestamp = msg.getJMSTimestamp()
            )

            cfg.system.actorOf(Props[FileDropActor]).tell(cmd, self)
            context.become(executing(msg, cmd))
          }
      }
    }

    def executing(msg: Message, cmd: FileDropCommand) : Receive = {
      case result : FileDropResult => result.success match {
        case true => context.stop(self)
        case false =>
          log.error(s"Error dropping msg [${msg.getJMSMessageID()}] to file.")
          sendErrorMessage(msg)
          context.stop(self)
      }
    }
  }

  private[this] val log = LoggerFactory.getLogger(classOf[JMSFileDropHandler])

  override def handleMessage(msg: Message): Option[Throwable] = {
    cfg.system.actorOf(Props[JMSFileDropActor]) ! msg
    None
  }
}


