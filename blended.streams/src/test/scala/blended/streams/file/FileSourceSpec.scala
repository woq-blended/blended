package blended.streams.file

import java.io.{File, FileOutputStream, FilenameFilter}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Source
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.streams.{FlowProcessor, StreamFactories}
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{AckProcessor, Collector}
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import com.typesafe.config.Config
import org.apache.commons.io.FileUtils
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.collection.JavaConverters._

@RequiresForkedJVM
class FileSourceSpec extends SimplePojoContainerSpec
  with PojoSrTestHelper
  with LoggingFreeSpecLike
  with Matchers {

  override def baseDir: String = s"${BlendedTestSupport.projectTestOutput}/container"

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  implicit val timeout : FiniteDuration = 1.second
  private val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](registry)(None)
  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val materializer : Materializer = ActorMaterializer()
  private val log : Logger = Logger[FileSourceSpec]

  private def prepareDirectory(dir : String) : File = {

    val f = new File(dir)

    FileUtils.deleteDirectory(f)
    f.mkdirs()

    f
  }

  private def genFile(f: File) : Unit = {
    val os = new FileOutputStream(f)
    os.write("Hallo Andreas".getBytes())
    os.flush()
    os.close()
  }

  private def listFiles(dir : String, pattern : String) : List[File] = {

    val fDir : File = new File(dir)

    val filter : FilenameFilter = new FilenameFilter {
      override def accept(d: File, name: String): Boolean = {
        name.matches(pattern)
      }
    }

    fDir.listFiles(filter).toList
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

      listFiles(pollCfg.sourceDir, ".*") should be (empty)
    }

    "perform a regular file poll from a given directory(bulk)" in {

      val numMsg : Int = 5000
      val t : FiniteDuration = 5.seconds

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(sourceDir = BlendedTestSupport.projectTestOutput + "/bulkPoll" )
      prepareDirectory(pollCfg.sourceDir)
      1.to(numMsg).foreach{ i => genFile(new File(pollCfg.sourceDir, s"test_$i.txt")) }

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg)).via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, t){ env => }

      val result : List[FlowEnvelope] = Await.result(collector.result, t + 100.millis)

      result should have size(numMsg)

      listFiles(pollCfg.sourceDir, ".*") should be (empty)
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

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, timeout){ env => }
      Await.result(collector.result, timeout + 100.millis)

      listFiles(pollCfg.sourceDir, ".*").map(_.getName()) should be (List("test.txt"))
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

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, timeout){ env => }
      Await.result(collector.result, timeout + 100.millis)

      listFiles(pollCfg.backup.get, ".*").map(_.getName()) should have size(1)
    }

    "do not process files if the lock file exists (relative)" in pending
    "do not process files if the lock file exists (absolute" in pending
    "allow to FileAckSources to process files in parallel" in pending
  }
}
