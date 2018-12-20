package blended.file
import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.jms.{JmsDeliveryMode, JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.{Success, Try}

class JMSFilePollHandler(
  cf: IdAwareConnectionFactory,
  dest: String,
  deliveryMode: Int,
  priority: Int,
  ttl: Long,
  props: Map[String, String]
) extends FilePollHandler with JmsStreamSupport {

  private val log : Logger = Logger[JMSFilePollHandler]

  private def createEnvelope(cmd : FileProcessCmd, file : File) : FlowEnvelope = {

    val body : ByteString = ByteString(Source.fromFile(file).mkString)
    val header : Map[String, MsgProperty] = props.mapValues(v => MsgProperty(v))

    FlowEnvelope(FlowMessage(body)(header))
      .withHeader("BlendedFileName", file.getName()).get
      .withHeader("BlendedFilePath", file.getAbsolutePath()).get
  }

  override def processFile(cmd: FileProcessCmd, f: File)(implicit system: ActorSystem): Try[Unit] = Try {

    implicit val materializer : Materializer = ActorMaterializer()
    implicit val eCtxt : ExecutionContext = system.dispatcher

    val env : FlowEnvelope = createEnvelope(cmd, f)

    val settings : JmsProducerSettings = JmsProducerSettings(
      connectionFactory = cf,
      jmsDestination = Some(JmsDestination.create(dest).get),
      deliveryMode = JmsDeliveryMode.Persistent
    )

    sendMessages(
      producerSettings = settings,
      log = log,
      env
    ) match {
      case Success(s) => s.shutdown()
      case f => f
    }
  }
}
