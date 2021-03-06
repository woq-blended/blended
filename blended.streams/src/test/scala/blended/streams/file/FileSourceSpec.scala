package blended.streams.file

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{AckProcessor, Collector}
import blended.streams.{FlowProcessor, StreamFactories}
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import com.typesafe.config.Config

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

@RequiresForkedJVM
class FileSourceSpec extends AbstractFileSourceSpec {

  "The FilePollSource should" - {

    "perform a regular file poll from a given directory" in {
      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

      val rawCfg : Config = ctCtxt.containerConfig.getConfig("simplePoll")
      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, ctCtxt).copy(sourceDir = BlendedTestSupport.projectTestOutput + "/simplePoll")
      prepareDirectory(pollCfg.sourceDir)

      val testFile : File = new File(pollCfg.sourceDir, "test.txt")
      genFile(testFile)

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg, envLogger(log))).via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, Some(timeout))

      val result : List[FlowEnvelope] = Await.result(collector.result, timeout + 100.millis)

      result should have size 1

      val env = result.head
      env.header[String]("BlendedFileName") should be(Some(testFile.getName()))
      env.header[String]("BlendedFilePath") should be(Some(testFile.getAbsolutePath()))

      env.id should endWith(testFile.getName())

      getFiles(dirName = pollCfg.sourceDir, pattern = ".*", recursive = false) should be(empty)
    }

    "restore the original file if the envelope was denied" in {
      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg(ctCtxt), ctCtxt).copy(sourceDir = BlendedTestSupport.projectTestOutput + "/restore")
      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg, envLogger(log)))
          .via(FlowProcessor.fromFunction("simplePoll.fail", envLogger(log)){ env => Try {
            throw new Exception("boom")
          }})
          .via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, Some(timeout))
      Await.result(collector.result, timeout + 100.millis)

      getFiles(pollCfg.sourceDir, pattern = ".*", recursive = false).map(_.getName()) should be(List("test.txt"))
    }

    "create a backup file if the backup directory is configured" in {
      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg(ctCtxt), ctCtxt).copy(
        sourceDir = BlendedTestSupport.projectTestOutput + "/backupts",
        backup = Some(BlendedTestSupport.projectTestOutput + "/backupts/backup")
      )
      prepareDirectory(pollCfg.backup.get)
      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val src : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg, envLogger(log))).via(new AckProcessor("simplePoll.ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, Some(timeout))
      Await.result(collector.result, timeout + 100.millis)

      getFiles(pollCfg.backup.get, pattern = ".*", recursive = false).map(_.getName()) should have size 1
    }

    "create a backup without timestamp suffix file if the backup directory is configured" in {
      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

      val pollCfg: FilePollConfig = FilePollConfig(rawCfg(ctCtxt), ctCtxt).copy(
        sourceDir = BlendedTestSupport.projectTestOutput + "/backup",
        backup = Some(BlendedTestSupport.projectTestOutput + "/backup/backup"),
        backupTimestamp = false
      )
      prepareDirectory(pollCfg.backup.get)
      prepareDirectory(pollCfg.sourceDir)
      genFile(new File(pollCfg.sourceDir, "test.txt"))

      val src: Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(new FileAckSource(pollCfg, envLogger(log))).via(new AckProcessor("simplePoll.ack").flow)

      val collector: Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("simplePoll", src, Some(timeout))
      Await.result(collector.result, timeout + 100.millis)

      getFiles(pollCfg.backup.get, pattern = "test.txt", recursive = false).map(_.getName()) should have size (1)
    }

    "do not process files if the lock file exists (relative)" in {

      val srcDir : File = new File(BlendedTestSupport.projectTestOutput + "lockrel")
      val lockFile : File = new File(srcDir, "lock.dat")

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg(ctCtxt), ctCtxt).copy(
        sourceDir = srcDir.getAbsolutePath(),
        lock = Some("./lock.dat")
      )

      testWithLock(srcDir, lockFile, pollCfg)
    }

    "do not process files if the lock file exists (absolute)" in {
      val srcDir : File = new File(BlendedTestSupport.projectTestOutput + "lockabs")
      val lockFile : File = new File(srcDir, "lock.dat")

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg(ctCtxt), ctCtxt).copy(
        sourceDir = srcDir.getAbsolutePath(),
        lock = Some(lockFile.getAbsolutePath())
      )

      testWithLock(srcDir, lockFile, pollCfg)
    }
  }
}
