package blended.file

import java.io.{File, FileFilter}

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import blended.testsupport.TestActorSys
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration._

class FileProcessActorSpec extends FreeSpec with Matchers {

  val fish : Boolean => FileProcessCmd => PartialFunction[Any, Boolean] = expected => cmd => {
    case p : FileProcessResult =>
      p.t.isEmpty.equals(expected) && p.cmd.copy(workFile = None).equals(cmd.copy(workFile = None))
    case _ =>
      false
  }

  "The FileProcessActor should" - {

    "process a single file and delete it on success if no archive dir is set" in TestActorSys { testkit =>

      implicit val system : ActorSystem = testkit.system

      val cfg = FilePollConfig(system.settings.config.getConfig("blended.file.poll")).copy(
        sourceDir = System.getProperty("projectTestOutput") + "/actor"
      )

      val srcFile = new File(cfg.sourceDir, "test.txt")

      val probe = TestProbe()
      val evtProbe = TestProbe()

      system.eventStream.subscribe(evtProbe.ref, classOf[FileProcessResult])

      val handler : SucceedingFileHandler = new SucceedingFileHandler()
      val cmd = FileProcessCmd(originalFile = new File(cfg.sourceDir, "test.txt"), cfg = cfg, handler = handler)

      system.actorOf(Props[FileProcessActor]).tell(cmd, probe.ref)

      probe.fishForMessage(1.second)(fish(true)(cmd))
      evtProbe.fishForMessage(1.second)(fish(true)(cmd))

      handler.handled should have size(1)
      srcFile.exists() should be (false)
    }

    "process a single file and move it to the archive dir if an archive dir is set" in TestActorSys { testkit =>
      implicit val system : ActorSystem = testkit.system

      val archiveDir = new File(System.getProperty("projectTestOutput") + "/archive")
      archiveDir.mkdirs()
      val oldArchiveDirSize = archiveDir.listFiles(new FileFilter {
        override def accept(fileName : File): Boolean = {
          fileName.getName.startsWith("test.xml")
        }
      }).size

      val cfg = FilePollConfig(system.settings.config.getConfig("blended.file.poll")).copy(
        sourceDir = System.getProperty("projectTestOutput") + "/actor",
        backup = Some(archiveDir.getAbsolutePath)
      )

      val srcFile = new File(cfg.sourceDir, "test.xml")

      val probe = TestProbe()
      val evtProbe = TestProbe()

      system.eventStream.subscribe(evtProbe.ref, classOf[FileProcessResult])

      val handler : SucceedingFileHandler = new SucceedingFileHandler()
      val cmd = FileProcessCmd(originalFile = srcFile, cfg = cfg, handler = handler)

      system.actorOf(Props[FileProcessActor]).tell(cmd, probe.ref)

      probe.fishForMessage(1.second)(fish(true)(cmd))
      evtProbe.fishForMessage(1.second)(fish(true)(cmd))

      archiveDir.listFiles(new FileFilter {
        override def accept(fileName : File): Boolean = {
          fileName.getName.startsWith("test.xml")
        }
      }) should have size (1 + oldArchiveDirSize)

      handler.handled should have size(1)
      srcFile.exists() should be (false)
    }

    "Restore the original file if the FilePollHandler throws an Exception" in TestActorSys { testkit =>

      implicit val system : ActorSystem = testkit.system

      val cfg = FilePollConfig(system.settings.config.getConfig("blended.file.poll")).copy(
        sourceDir = System.getProperty("projectTestOutput") + "/poll",
        tmpExt = "_temp"
      )

      val srcFile = new File(cfg.sourceDir, "test.txt")

      val probe = TestProbe()
      val evtProbe = TestProbe()

      system.eventStream.subscribe(evtProbe.ref, classOf[FileProcessResult])

      val cmd = FileProcessCmd(originalFile = srcFile, cfg = cfg, handler = new FailingFileHandler())

      system.actorOf(Props[FileProcessActor]).tell(cmd, probe.ref)

      probe.fishForMessage(1.second)(fish(false)(cmd))
      evtProbe.fishForMessage(1.second)(fish(false)(cmd))

      srcFile.exists() should be (true)
    }
  }

}
