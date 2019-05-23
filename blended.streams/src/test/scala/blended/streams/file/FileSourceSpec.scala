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
    pollFiles(timeout * 3) should have size 1
  }

  "The FilePollSource should" - {

    val rawCfg : Config = idSvc.getContainerContext().getContainerConfig().getConfig("simplePoll")

    "perform a regular file poll from a given directory" in {

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(sourceDir = BlendedTestSupport.projectTestOutput + "/simplePoll" )
      prepareDirectory(pollCfg.sourceDir)

      val testFile : File = new File(pollCfg.sourceDir, "test.txt")
      genFile(testFile)

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg)).via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, timeout){ env => }

      val result : List[FlowEnvelope] = Await.result(collector.result, timeout + 100.millis)

      result should have size 1

      val env = result.head
      env.header[String]("BlendedFileName") should be (Some(testFile.getName()))
      env.header[String]("BlendedFilePath") should be (Some(testFile.getAbsolutePath()))

      env.id should endWith (testFile.getName())

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

      result should have size numMsg

      getFiles(dirName = pollCfg.sourceDir, pattern = ".*", recursive = false) should be (empty)
    }

    "restore the original file if the envelope was denied" in {

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(sourceDir = BlendedTestSupport.projectTestOutput + "/restore" )
      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg))
          .via(FlowProcessor.fromFunction("simplePoll.fail", log){ _ => Try {
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

      getFiles(pollCfg.backup.get, pattern = ".*", recursive = false).map(_.getName()) should have size 1
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
      val numMsg : Int = 5000
      val t : FiniteDuration = 5.seconds

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc)
        .copy(sourceDir = BlendedTestSupport.projectTestOutput + "/parallel" )

      prepareDirectory(pollCfg.sourceDir)
      1.to(numMsg).foreach{ i => genFile(new File(pollCfg.sourceDir, s"test_$i.txt")) }

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg)).async.via(new AckProcessor("simplePoll.ack").flow)

      val collector1 : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("parallel1", src, 200.millis){ _ => }
      val collector2 : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("parallel2", src, t){ _ => }

      val result1 : List[FlowEnvelope] = Await.result(collector1.result, 300.millis)
      val result2 : List[FlowEnvelope] = Await.result(collector2.result, t + 100.millis)

      assert(result1.size >= 0)
      assert(result2.size >= 0)

      assert(result1.size + result2.size >= numMsg)

      getFiles(dirName = pollCfg.sourceDir, pattern = ".*", recursive = false) should be (empty)
    }
  }
}
