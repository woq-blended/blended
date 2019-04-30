package blended.file
import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.io.{BufferedSource, Source}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class JMSFilePollHandler(
  settings : JmsProducerSettings,
  header : FlowMessage.FlowMessageProps
) extends FilePollHandler with JmsStreamSupport {

  private val log : Logger = Logger[JMSFilePollHandler]

  private def createEnvelope(cmd : FileProcessCmd, file : File) : Try[FlowEnvelope] = {

    val src : BufferedSource = Source.fromFile(file)

    try {
      val body : ByteString = ByteString(src.mkString)
      src.close()

      Success(FlowEnvelope(FlowMessage(body)(header))
        .withHeader("BlendedFileName", cmd.f.getName()).get
        .withHeader("BlendedFilePath", cmd.f.getAbsolutePath()).get)
    } catch {
      case NonFatal(t) => Failure(t)
    } finally {
      src.close()
    }
  }

  override def processFile(cmd: FileProcessCmd, f : File)(implicit system: ActorSystem): Try[Unit] = Try {

    implicit val materializer : Materializer = ActorMaterializer()
    implicit val eCtxt : ExecutionContext = system.dispatcher

    createEnvelope(cmd, f) match {
      case Success(env) =>
        log.trace(s"Handling polled file in JMSHandler : [${env.flowMessage.header.mkString(",")}]")

        sendMessages(
          producerSettings = settings,
          log = log,
          env
        ) match {
          case Success(s) => Success(s.shutdown())
          case Failure(t) => Failure(t)
        }
      case Failure(t) => Failure(t)
    }
  }
}
