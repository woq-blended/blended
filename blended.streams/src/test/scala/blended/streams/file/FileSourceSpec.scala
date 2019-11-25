package blended.streams.file

import java.io.File

import akka.NotUsed
import akka.stream.scaladsl.Source
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{AckProcessor, Collector}
import blended.streams.{FlowProcessor, StreamFactories}
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

@RequiresForkedJVM
class FileSourceSpec extends AbstractFileSourceSpec {

  "The FilePollSource should" - {

    "perform a regular file poll from a given directory" in {

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(sourceDir = BlendedTestSupport.projectTestOutput + "/simplePoll" )
      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg, envLogger)).via(new AckProcessor("simplePoll.ack").flow)

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
        Source.fromGraph(new FileAckSource(pollCfg, envLogger)).via(new AckProcessor("simplePoll.ack").flow)

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
        Source.fromGraph(new FileAckSource(pollCfg, envLogger))
          .via(FlowProcessor.fromFunction("simplePoll.fail", envLogger){ env => Try {
            throw new Exception("boom")
          }})
          .via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, timeout){ _ => }
      Await.result(collector.result, timeout + 100.millis)

      getFiles(pollCfg.sourceDir, pattern = ".*", recursive = false).map(_.getName()) should be (List("test.txt"))
    }

    "create a backup file if the backup directory is configured" in {
      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(
        sourceDir = BlendedTestSupport.projectTestOutput + "/backupts",
        backup = Some(BlendedTestSupport.projectTestOutput + "/backupts/backup")
      )
      prepareDirectory(pollCfg.backup.get)
      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg, envLogger)).via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, timeout){ _ => }
      Await.result(collector.result, timeout + 100.millis)

      getFiles(pollCfg.backup.get, pattern = ".*", recursive = false).map(_.getName()) should have size(1)
    }

    "create a backup without timestamp suffix file if the backup directory is configured" in {
      val pollCfg: FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(
        sourceDir = BlendedTestSupport.projectTestOutput + "/backup",
        backup = Some(BlendedTestSupport.projectTestOutput + "/backup/backup"),
        backupTimestamp = false
      )
      prepareDirectory(pollCfg.backup.get)
      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val src: Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg, envLogger)).via(new AckProcessor("simplePoll.ack").flow)

      val collector: Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, timeout) { _ => }
      Await.result(collector.result, timeout + 100.millis)

      getFiles(pollCfg.backup.get, pattern = "test.txt", recursive = false).map(_.getName()) should have size (1)
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
  }
}
