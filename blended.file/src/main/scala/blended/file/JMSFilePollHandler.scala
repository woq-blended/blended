package blended.file

import java.nio.charset.Charset

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Source, Zip}
import blended.akka.MemoryStash
import blended.streams.file.FilePollConfig
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.{AbstractStreamController, StreamController, StreamControllerConfig}
import blended.util.FileHelper
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * A [[FilePollHandler]] that sends the contents of processed files to a JMS destination.
  */
class JMSFilePollHandler(
  cfg : FilePollConfig,
  settings : JmsProducerSettings,
  header : FlowMessage.FlowMessageProps
)(implicit system: ActorSystem) extends FilePollHandler with JmsStreamSupport {

  private val log : Logger = Logger[JMSFilePollHandler]
  private var processActor : Option[ActorRef] = None

  def start() : Unit = { processActor.synchronized {
    if (processActor.isEmpty) {
      processActor = Some(system.actorOf(AsyncSendActor.props(cfg, settings, header)))
    }
  }}

  def stop() : Unit = {
    processActor.synchronized{
      processActor.foreach(system.stop)
      processActor = None
    }
  }

  // TODO: Watch AsyncSendActor and implement Supervisor
  override def processFile(cmd: FileProcessCmd) : Future[FileProcessResult] = {

    log.debug(s"Processing [$cmd]")

    val p : Promise[FileProcessResult] = Promise[FileProcessResult]()

    processActor match {
      case None =>
        val msg = s"Actor to process file [${cmd.fileToProcess}] for [${cmd.id}] is not available - perhaps the ${getClass().getName()} has not been started ?"
        log.warn(msg)
        // Looks a bit strange, but the promise is successful with a FileProcessResult that carries
        // the an exception.
        p.success(FileProcessResult(cmd, Some(new Exception(msg))))
      case Some(a) =>
        // If the AsyncSendActor is available, we use it to fulfill the promise
        a ! (cmd, p)
    }

    p.future
  }
}

/* ---------------------------------------------------------------------------------------- */

private case class FileSendInfo(
  actor : ActorRef,
  cmd : FileProcessCmd,
  env : FlowEnvelope,
  p : Promise[FileProcessResult]
)

private case class JmsStreamStarted(entry: ActorRef)
private case object JmsStreamStopped

object AsyncSendActor {

  def props(
    cfg : FilePollConfig,
    settings : JmsProducerSettings,
    header : FlowMessage.FlowMessageProps
  ) : Props = Props(new AsyncSendActor(cfg, settings, header))
}

class AsyncSendActor(
  cfg : FilePollConfig,
  settings : JmsProducerSettings,
  header : FlowMessage.FlowMessageProps
) extends Actor with ActorLogging with MemoryStash {

  private implicit val system : ActorSystem = context.system
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()

  case object Start

  override def preStart(): Unit = self ! Start

  private def createEnvelope(cmd : FileProcessCmd) : Try[FlowEnvelope] = Try {

    val bytes : Array[Byte] = FileHelper.readFile(cmd.fileToProcess.getAbsolutePath())

    val msg : FlowMessage = if (cmd.cfg.asText) {
      val charSet : Charset = cmd.cfg.charSet match {
        case None => Charset.defaultCharset()
        case Some(s) => Charset.forName(s)
      }

      log.debug(s"Using charset [${charSet.displayName()}] to create text message.")

      FlowMessage(new String(bytes, charSet))(header)
    } else {
      FlowMessage(bytes)(header)
    }

    val env : FlowEnvelope = FlowEnvelope(msg, cmd.id)
      .withHeader(cmd.cfg.filenameProp, cmd.originalFile.getName()).get
      .withHeader(cmd.cfg.filepathProp, cmd.originalFile.getAbsolutePath()).get

    log.debug(s"Created Envelope [$env] from [$cmd]")

    env
  }

  override def receive: Receive = starting.orElse(stashing)

  private def starting : Receive = {
    case Start =>
      log.info(s"Starting Async Send Actor...")
      val streamCfg : StreamControllerConfig = StreamControllerConfig(
        name = cfg.id + "-stream",
        minDelay = 10.seconds,
        maxDelay = 1.minute,
        exponential = true,
        onFailureOnly = true,
        random = 0.2
      )
      context.actorOf(JmsSendStream.props(cfg, streamCfg, settings, self))
      context.become(withoutStream)
  }

  private def withStream(streamController : ActorRef, jmsSendActor : ActorRef) : Receive = {
    case JmsStreamStopped =>

    case (cmd : FileProcessCmd, p : Promise[FileProcessResult]) =>

      log.debug(s"Processing [$cmd]")

      createEnvelope(cmd) match {
        case Success(env) =>
          jmsSendActor ! FileSendInfo(self, cmd, env, p)

        case Failure(t) =>
          log.warning(s"Failed to create envelope for [$cmd] : [${t.getMessage()}]")
          p.success(FileProcessResult(cmd, Some(t)))
      }

    case info : FileSendInfo =>
      info.env.exception match {
        case None =>
          log.info(s"Successfully sent file [${info.cmd.id}] to JMS : [${info.cmd}]")
          info.p.success(FileProcessResult(info.cmd, None))

        case Some(t) =>
          log.warning(s"Failed to send file content for [${info.cmd}] : [${t.getMessage()}]")
          info.p.success(FileProcessResult(info.cmd, Some(t)))
      }
  }

  private def withoutStream : Receive = {

    case JmsStreamStarted(ref) =>
      context.become(withStream(sender(), ref))

    case (cmd : FileProcessCmd, p : Promise[FileProcessResult]) =>
      p.success(FileProcessResult(cmd, Some(new Exception("JMS Stream is not currently connected."))))
  }
}

/* ------------------------------------------------------------------------------ */

private object JmsSendStream {

  def props(
    pollCfg : FilePollConfig,
    streamCfg : StreamControllerConfig,
    settings : JmsProducerSettings,
    asyncSender : ActorRef
  )(implicit system : ActorSystem, materializer: Materializer) : Props =
    Props(new JmsSendStream(pollCfg, streamCfg, settings, asyncSender))
}

private class JmsSendStream(
  pollCfg : FilePollConfig,
  streamCfg : StreamControllerConfig,
  settings: JmsProducerSettings,
  asyncSender : ActorRef
)(implicit system : ActorSystem, materializer: Materializer)
  extends AbstractStreamController[FileSendInfo, ActorRef](streamCfg)
  with JmsStreamSupport {

  private var entry : Option[ActorRef] = None

  private val log : Logger = Logger[JmsSendStream]
  private implicit val eCtxt : ExecutionContext = system.dispatcher

  override def preStart(): Unit = self ! StreamController.Start

  override def afterStreamStarted(mat : ActorRef): Unit = {
    log.debug(s"JMS Stream for file poller [${pollCfg.id}] started.")
    entry = Some(mat)
    entry.foreach{ ref => asyncSender ! JmsStreamStarted(ref) }
  }

  override def beforeStreamRestart(): Unit = {
    log.debug(s"JMS Stream for file poller [${pollCfg.id}] stopped.")
    entry = None
    asyncSender ! JmsStreamStopped
  }

  override def receive: Receive = starting(streamCfg, streamCfg.minDelay)

  // We create an outbound JMS Stream with an actor serving as the entryPoint
  private val pollingSrc : Source[FileSendInfo, ActorRef] =
    Source.actorRef[FileSendInfo](pollCfg.batchSize * 2, overflowStrategy = OverflowStrategy.fail)

  private val performSend : Flow[FileSendInfo, FileSendInfo, NotUsed] = {

    val g : Graph[FlowShape[FileSendInfo, FileSendInfo], NotUsed] = GraphDSL.create() { implicit b=>

      import GraphDSL.Implicits._

      // First we split the flow, so that we can keep the file info in one part
      // and perform the send in the other part, collecting any exceptions that
      // may occur
      val split = b.add(Broadcast[FileSendInfo](2))

      // to send the jms message we need to select the envelope
      val select = b.add(Flow.fromFunction[FileSendInfo, FlowEnvelope](_.env))

      // then we perform the jms send
      val jmsSend = b.add(jmsProducer(
        name = "filesend",
        settings = settings
      ))

      split.out(0) ~> select ~> jmsSend.in

      // Finally we zip the branches
      val zip = b.add(Zip[FlowEnvelope, FileSendInfo])

      jmsSend.out ~> zip.in0
      split.out(1) ~> zip.in1

      val merge = b.add(Flow.fromFunction[(FlowEnvelope, FileSendInfo), FileSendInfo]{ p => p._2.copy(env = p._1)})
      zip.out ~> merge.in

      FlowShape(split.in, merge.out)
    }

    Flow.fromGraph(g)
  }

  // This is to send the completed info object back to the controlling actor after sending the message
  // The flow envelope will have an exception set if the send has failed
  val respond : Flow[FileSendInfo, FileSendInfo, NotUsed] = Flow.fromFunction[FileSendInfo, FileSendInfo]{ info =>
    info.actor ! info
    info
  }

  override def source(): Source[FileSendInfo, ActorRef] = pollingSrc.via(performSend).via(respond)
}


