package blended.file
import java.io.File

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.util.ByteString
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.util.logging.Logger

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.io.BufferedSource
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class AsyncSendActor extends Actor {

  private val inflight : mutable.Map[String, FileProcessCmd] = mutable.Map.empty

  override def receive: Receive = {
    case cmd : FileProcessCmd =>
    case env : FlowEnvelope =>
  }
}

class JMSFilePollHandler(
  settings : JmsProducerSettings,
  header : FlowMessage.FlowMessageProps,
  bufferSize : Int
) extends FilePollHandler with JmsStreamSupport {

  private val log : Logger = Logger[JMSFilePollHandler]

  def start() : Unit = {

    // Create an actor where we can send FileProcessCommands to
    val actorSrc : Source[FileProcessCmd, ActorRef] = Source.actorRef[FileProcessCmd](bufferSize, OverflowStrategy.backpressure)


  }

  private def createEnvelope(cmd : FileProcessCmd, file : File) : Try[FlowEnvelope] = {

    val src : BufferedSource = scala.io.Source.fromFile(file)

    try {
      val body : ByteString = ByteString(src.mkString)
      src.close()

      val msg : FlowMessage = FlowMessage(body)(header)
        .withHeader("BlendedFileName", cmd.originalFile.getName()).get
        .withHeader("BlendedFilePath", cmd.originalFile.getAbsolutePath()).get

      Success(FlowEnvelope(msg, cmd.id))
    } catch {
      case NonFatal(t) => Failure(t)
    } finally {
      src.close()
    }
  }

  override def processFile(cmd: FileProcessCmd, f : File)(implicit system: ActorSystem): Future[(FileProcessCmd, Option[Throwable])] = {

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
          case Success(s) => Future((cmd, None))
          case Failure(t) => Future((cmd, Some(t)))
        }
      case Failure(t) => Future((cmd, Some(t)))
    }
  }
}
