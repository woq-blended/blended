package blended.streams.file

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestProbe
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{AckProcessor, Collector}
import blended.streams.{FlowProcessor, StreamFactories}
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, FileTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import com.typesafe.config.Config
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

@RequiresForkedJVM
class FileSourceSpec extends SimplePojoContainerSpec
  with PojoSrTestHelper
  with LoggingFreeSpecLike
  with Matchers
  with FileTestSupport
  with FileSourceTestSupport {

  override def baseDir: String = s"${BlendedTestSupport.projectTestOutput}/container"

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  implicit val timeout : FiniteDuration = 1.second
  private val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](registry)(None)
  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()
  private val log : Logger = Logger[FileSourceSpec]


  private def testWithLock(srcDir : File, lockFile : File, pollCfg : FilePollConfig): Unit = {

    case class FilePolled(env : FlowEnvelope)

    def pollFiles(t : FiniteDuration) : List[FlowEnvelope] = {
      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg))
          .via(FlowProcessor.fromFunction("event", log){ env => Try {
            system.eventStream.publish(FilePolled(env))
            env
          }})
          .via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, t){ _ => }
      Await.result(collector.result, t + 100.millis)
    }

    val probe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[FilePolled])

    prepareDirectory(pollCfg.sourceDir)
    genFile(lockFile)
    akka.pattern.after(timeout + 200.millis, system.scheduler)(Future {
      log.info(s"Removing file [${lockFile.getAbsolutePath()}]")
      lockFile.delete()
    })

    genFile(new File(pollCfg.sourceDir, "test.txt"))

    // make sure we do not receive any messages before the lock file is removed
    probe.expectNoMessage(timeout)
    pollFiles(timeout * 3) should have size(1)
  }

  "The FilePollSource should" - {

    val rawCfg : Config = idSvc.getContainerContext().getContainerConfig().getConfig("simplePoll")

    "perform a regular file poll from a given directory" in {

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(sourceDir = BlendedTestSupport.projectTestOutput + "/simplePoll" )
      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg)).via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, timeout){ env => }

      val result : List[FlowEnvelope] = Await.result(collector.result, timeout + 100.millis)

      result should have size(1)

      getFiles(dirName = pollCfg.sourceDir, pattern = ".*", recursive = false) should be (empty)
    }

    "perform a regular file poll from a given directory(bulk)" in {

      val numMsg : Int = 5000
      val t : FiniteDuration = 5.seconds

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(sourceDir = BlendedTestSupport.projectTestOutput + "/bulkPoll" )
      prepareDirectory(pollCfg.sourceDir)
      1.to(numMsg).foreach{ i => genFile(new File(pollCfg.sourceDir, s"test_$i.txt")) }

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg)).via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, t){ _ => }

      val result : List[FlowEnvelope] = Await.result(collector.result, t + 100.millis)

      result should have size(numMsg)

      getFiles(dirName = pollCfg.sourceDir, pattern = ".*", recursive = false) should be (empty)
    }

    "restore the original file if the envelope was denied" in {

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(sourceDir = BlendedTestSupport.projectTestOutput + "/restore" )
      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg))
          .via(FlowProcessor.fromFunction("simplePoll.fail", log){ env => Try {
            throw new Exception("boom")
          }})
          .via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, timeout){ _ => }
      Await.result(collector.result, timeout + 100.millis)

      getFiles(pollCfg.sourceDir, pattern = ".*", recursive = false).map(_.getName()) should be (List("test.txt"))
    }

    "create a backup file if the backup directory is configured" in {
      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(
        sourceDir = BlendedTestSupport.projectTestOutput + "/backup",
        backup = Some(BlendedTestSupport.projectTestOutput + "/backup/backup")
      )
      prepareDirectory(pollCfg.backup.get)
      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg)).via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, timeout){ _ => }
      Await.result(collector.result, timeout + 100.millis)

      getFiles(pollCfg.backup.get, pattern = ".*", recursive = false).map(_.getName()) should have size(1)
    }

    "do not process files if the lock file exists (relative)" in {

      val srcDir : File = new File(BlendedTestSupport.projectTestOutput + "lockrel")
      val lockFile : File = new File(srcDir, "lock.dat")

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(
        sourceDir = srcDir.getAbsolutePath(),
        lock = Some("./lock.dat")
      )

      testWithLock(srcDir, lockFile, pollCfg)
    }

    "do not process files if the lock file exists (absolute)" in {
      val srcDir : File = new File(BlendedTestSupport.projectTestOutput + "lockabs")
      val lockFile : File = new File(srcDir, "lock.dat")

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(
        sourceDir = srcDir.getAbsolutePath(),
        lock = Some(lockFile.getAbsolutePath())
      )

      testWithLock(srcDir, lockFile, pollCfg)
    }

    "allow to FileAckSources to process files in parallel" in {

      val numSrc : Int = 5
      val numMsg : Int = 10000
      val t : FiniteDuration = 5.seconds

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc)
        .copy(sourceDir = BlendedTestSupport.projectTestOutput + "/parallel" )

      def countWords(l : Seq[String]) : Map[String, Int] = l.foldLeft(Map.empty[String, Int]){ (current, s) =>
        current.get(s) match {
          case None => current + (s -> 1)
          case Some(v) => current.filterKeys(_ != s) + (s -> (v + 1))
        }
      }

      def createCollector(subId : Int, startDelay : Option[FiniteDuration] = None) : Collector[FlowEnvelope] = {
        val src : Source[FlowEnvelope, NotUsed] =
          Source.fromGraph(new FileAckSource(
            pollCfg.copy(id = s"poller$subId", interval = 100.millis)
          )).async.via(new AckProcessor(s"simplePoll$subId.ack").flow)

        startDelay.foreach(d => Thread.sleep(d.toMillis))
        StreamFactories.runSourceWithTimeLimit("parallel1", src, t){ _ => }
      }

      prepareDirectory(pollCfg.sourceDir)
      1.to(numMsg).foreach{ i => genFile(new File(pollCfg.sourceDir, s"test_$i.txt")) }

      val results : Seq[Future[List[FlowEnvelope]]] = 0.until(numSrc).map{ i=>
        val coll : Collector[FlowEnvelope] = createCollector(i, if (i == 0) None else Some(20.millis))
        coll.result
      }

      val combined : Future[Seq[List[FlowEnvelope]]] = Future.sequence(results)
      val allResults : Seq[String] = Await.result(combined, t + 100.millis).flatten.map(_.header[String]("BlendedFileName").get)

      val dups : Map[String, Int] = countWords(allResults).filter{ case (_, v) => v > 1 }
      dups should be (empty)

      assert(allResults.size == numMsg)

      getFiles(dirName = pollCfg.sourceDir, pattern = ".*", recursive = false) should be (empty)
    }
  }
}
