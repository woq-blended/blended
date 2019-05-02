package blended.file

import java.io.File

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import blended.akka.SemaphoreActor
import blended.testsupport.TestActorSys
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.Matchers

import scala.concurrent.duration._

class FilePollActorSpec extends AbstractFilePollSpec
  with Matchers
  with LoggingFreeSpecLike {

  override def handler()(implicit system : ActorSystem): FilePollHandler = new SucceedingFileHandler()

  private[this] def withBlocking(lockfile : String)(implicit system: ActorSystem) : Unit = {

    val sem : ActorRef = system.actorOf(Props[SemaphoreActor])

    val srcDir = new File(System.getProperty("projectTestOutput") + "/blocking")
    srcDir.mkdirs()

    val cfg = FilePollConfig(system.settings.config.getConfig("blended.file.poll")).copy(
      sourceDir = srcDir.getAbsolutePath(),
      lock = Some(lockfile)
    )

    val lockFile = if (lockfile.startsWith("./")) {
      new File(cfg.sourceDir, lockfile.substring(2))
    } else {
      new File(lockfile)
    }

    genFile(lockFile)

    val srcFile = new File(srcDir, "test.txt")
    genFile(srcFile)

    val probe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[FileProcessResult])
    system.actorOf(FilePollActor.props(cfg, handler(), Some(sem)))

    probe.expectNoMessage(3.seconds)
    srcFile.exists() should be (true)

    lockFile.delete()

    val processed = probe.expectMsgType[FileProcessResult]
    srcFile.exists() should be (false)

    processed.cmd.originalFile.getName() should be ("test.txt")
  }

  "The File Poller should" - {

    "do perform a regular poll and process files" in TestActorSys { testkit =>
      withMessages("pollspec", 5)(defaultTest)(testkit.system)
    }

    "do perform a regular poll and process files (bulk)" in TestActorSys { testkit =>
      withMessages("pollspec", 500)(defaultTest)(testkit.system)
    }

    "block the message processing if specified lock file exists (relative)" in TestActorSys { testkit =>
      withBlocking("./lock")(testkit.system)
    }

    "block the message processing if specified lock file exists (absolute)" in TestActorSys { testkit =>
      withBlocking("/tmp/lock")(testkit.system)
    }
  }
}

class FilePollActorFailSpec extends AbstractFilePollSpec
  with Matchers
  with LoggingFreeSpecLike {

  // A file processor that does the first step of renaming the file, but then never returns
  class NeverComeBackProcessor extends Actor {
    override def receive: Receive = {
      case cmd : FileProcessCmd =>
        val tempFile : File = new File(cmd.originalFile.getParentFile, cmd.originalFile.getName + cmd.cfg.tmpExt)
        context.actorOf(Props[FileManipulationActor]).tell(RenameFile(cmd.originalFile, tempFile), self)

      case _ =>
    }
  }

  override def handler()(implicit system : ActorSystem): FilePollHandler = new SucceedingFileHandler()

  // use a file poller with a completely unresponsive file processor
  override protected def filePoller(cfg: FilePollConfig)(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new FilePollActor(cfg, handler(), semaphore()) {
      override protected def fileProcessor(): ActorRef = context.actorOf(Props(new NeverComeBackProcessor()))
    }))

  "Restore the original messages if the FileProcessActor is unresponsive" in TestActorSys { testkit =>
    withMessages("failedPoll", msgCount = 5){ files => probe =>

      val processed : List[FileProcessResult] = probe.receiveWhile[FileProcessResult](max = 5.seconds, messages = files.size) {
        case fp : FileProcessResult => fp
      }.toList

      processed should be (empty)

      files.forall{ f => f.exists() } should be (true)

    }(testkit.system)
  }

}
