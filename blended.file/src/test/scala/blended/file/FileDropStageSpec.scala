package blended.file

import java.io.File

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import blended.streams.FlowHeaderConfig
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger, FlowMessage}
import blended.testsupport.FileTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class FileDropStageSpec extends LoggingFreeSpec
  with Matchers
  with FileEnvelopeHeader
  with FileTestSupport {

  private implicit val system : ActorSystem = ActorSystem(getClass().getSimpleName())
  private implicit val materializer : Materializer = ActorMaterializer()
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private val log : Logger = Logger[FileDropStageSpec]
  private val to : FiniteDuration = 1.second
  private val headerCfg = FlowHeaderConfig.create(prefix = "App")
  private val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerCfg, log)

  val prepareDropper : FileDropConfig => String => FileDropConfig = cfg => subDir => {
    val dir = cfg.defaultDir + "/" + subDir
    cleanUpDirectory(dir)
    cfg.copy(defaultDir = dir)
  }

  private val dropCfg : String => FileDropConfig = subDir => prepareDropper(FileDropConfig(
    dirHeader = "",
    fileHeader = fileHeader(headerCfg.prefix),
    compressHeader = compressHeader(headerCfg.prefix),
    appendHeader = appendHeader(headerCfg.prefix),
    charsetHeader = charsetHeader(headerCfg.prefix),
    defaultDir = System.getProperty("projectTestOutput", "/tmp"),
    dropTimeout = to
  ))(subDir)

  private val dropActor : ActorRef = system.actorOf(Props[FileDropActor])

  def dropFlow(cfg: FileDropConfig, bufferSize : Int): ((ActorRef, KillSwitch), Future[Seq[FileDropResult]]) = {

    val dropper : Flow[FlowEnvelope, FileDropResult, _] =
      Flow.fromGraph(new FileDropStage(
        name = "spec",
        config = cfg,
        headerCfg = headerCfg,
        dropActor = dropActor,
        log = envLogger
      ))

    Source.actorRef[FlowEnvelope](bufferSize, OverflowStrategy.fail)
      .viaMat(dropper)(Keep.left)
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(Sink.seq[FileDropResult])(Keep.both)
      .run()
  }

  def dropFiles(cfg : FileDropConfig, msgs : FlowEnvelope*) : Seq[FileDropResult] = {

    val ((actor, switch), results) = dropFlow(cfg, msgs.size)
    msgs.foreach(actor ! _)

    akka.pattern.after(to, system.scheduler) { Future(switch.shutdown()) }
    Await.result(results, to + 500.millis)
  }

  "The FileDropStage should" - {

    "drop an uncompressed file into the target directory in" in {

      val msgCount : Int = 50
      val cfg : FileDropConfig = dropCfg("dropStage")

      val content : Int => ByteString = i => ByteString(s"Hallo Blended [$i]")

      val envelopes : Seq[FlowEnvelope] = 1.to(msgCount).map { i =>
        FlowEnvelope(FlowMessage(content(i))(FlowMessage.props(
          cfg.fileHeader -> s"test-$i.txt"
        ).get))
      }

      val results : Seq[FileDropResult] = dropFiles(cfg, envelopes : _*)
      results should have size msgCount

      assert(results.forall(_.error.isEmpty))

      1.to(msgCount).foreach { i =>
        verifyTargetFile(new File(cfg.defaultDir, s"test-$i.txt"), content(i))
      }

    }
  }
}
