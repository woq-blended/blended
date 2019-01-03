package blended.file
import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.{Success, Try}

class JMSFilePollHandler(
  settings : JmsProducerSettings,
  header : FlowMessage.FlowMessageProps
) extends FilePollHandler with JmsStreamSupport {

  private val log : Logger = Logger[JMSFilePollHandler]

  private def createEnvelope(cmd : FileProcessCmd, file : File) : FlowEnvelope = {

    val body : ByteString = ByteString(Source.fromFile(file).mkString)

    FlowEnvelope(FlowMessage(body)(header))
      .withHeader("BlendedFileName", file.getName()).get
      .withHeader("BlendedFilePath", file.getAbsolutePath()).get
  }

  override def processFile(cmd: FileProcessCmd, f: File)(implicit system: ActorSystem): Try[Unit] = Try {

    implicit val materializer : Materializer = ActorMaterializer()
    implicit val eCtxt : ExecutionContext = system.dispatcher

    val env : FlowEnvelope = createEnvelope(cmd, f)

    log.trace(s"Handling polled file in JMSHandler : [${env.flowMessage.header.mkString(",")}]")

    sendMessages(
      producerSettings = settings,
      log = log,
      env
    ) match {
      case Success(s) => s.shutdown()
      case _ => // do nothing as the stream is already closed
    }
  }
}
