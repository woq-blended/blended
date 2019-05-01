package blended.file

import java.io.File

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import blended.akka.SemaphoreActor
import blended.testsupport.TestActorSys
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.Matchers

import scala.concurrent.duration._

class FilePollSpec extends TestKit(ActorSystem("JmsFilePoll"))
  with AbstractFilePollSpec
  with Matchers
  with LoggingFreeSpecLike {

  override def handler()(implicit system : ActorSystem): FilePollHandler = new SucceedingFileHandler()

  private[this] def withBlocking(lockfile : String, testkit: TestKit) : Unit = {

    implicit val system = testkit.system
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
      withMessages("pollspec", 5)
    }

    "do perform a regular poll and process files (bulk)" in TestActorSys { testkit =>
      withMessages("pollspec", 500)
    }

    "block the message processing if specified lock file exists (relative)" in TestActorSys { testkit =>
      withBlocking("./lock", testkit)
    }

    "block the message processing if specified lock file exists (absolute)" in TestActorSys { testkit =>
      withBlocking("/tmp/lock", testkit)
    }
  }
}
