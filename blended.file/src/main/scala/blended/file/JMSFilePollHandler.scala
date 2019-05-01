package blended.file
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.ByteString
import blended.akka.MemoryStash
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.util.logging.Logger

import scala.concurrent.{Future, Promise}
import scala.io.BufferedSource
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object AsyncSendActor {

  def props(
    settings : JmsProducerSettings,
    header : FlowMessage.FlowMessageProps
  ) : Props = Props(new AsyncSendActor(settings, header))
}

class AsyncSendActor(
  settings : JmsProducerSettings,
  header : FlowMessage.FlowMessageProps
) extends Actor with MemoryStash {

  private val log : Logger = Logger[AsyncSendActor]

  case object Start

  override def preStart(): Unit = self ! Start

  case class FileSendInfo(
    actor : ActorRef,
    cmd : FileProcessCmd,
    env : FlowEnvelope,
    p : Promise[FileProcessResult]
  )

  private def createEnvelope(cmd : FileProcessCmd) : Try[FlowEnvelope] = {

    val src : BufferedSource = scala.io.Source.fromFile(cmd.fileToProcess)

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

  override def receive: Receive = starting.orElse(stashing)

  private def starting : Receive = {
    case Start =>
      log.info(s"Starting Async Send Actor...")
      context.become(started)
  }

  private def started : Receive = {
    case (cmd : FileProcessCmd, p : Promise[FileProcessResult]) =>
      createEnvelope(cmd) match {
        case Success(env) =>
          self ! FileSendInfo(sender(), cmd, env, p)
        case Failure(t) =>
          p.failure(t)
      }
    case info : FileSendInfo =>
      info.env.exception match {
        case None =>
          log.info(s"Successfully processed file [${info.cmd.id}] : [${info.cmd}]")
          info.p.success(FileProcessResult(info.cmd, None))

        case Some(t) => info.p.failure(t)
      }
  }
}

class JMSFilePollHandler(
  settings : JmsProducerSettings,
  header : FlowMessage.FlowMessageProps,
  bufferSize : Int
)(implicit system: ActorSystem) extends FilePollHandler with JmsStreamSupport {

  private val log : Logger = Logger[JMSFilePollHandler]
  private var processActor : Option[ActorRef] = None

  def start() : Unit = { processActor.synchronized {
    if (processActor.isEmpty) {
      processActor = Some(system.actorOf(AsyncSendActor.props(settings, header)))
    }
  }}

  def stop() : Unit = {
    processActor.synchronized{
      processActor.foreach(system.stop)
      processActor = None
    }
  }

  override def processFile(cmd: FileProcessCmd) : Future[FileProcessResult] = {

    val p : Promise[FileProcessResult] = Promise[FileProcessResult]()

    processActor match {
      case None =>
        val msg = s"Actor to process file [${cmd.fileToProcess}] for [${cmd.id}] is not available"
        log.warn(msg)
        p.success(FileProcessResult(cmd, Some(new Exception(msg))))
      case Some(a) =>
        a ! (cmd, p)
    }

    p.future
  }
}
