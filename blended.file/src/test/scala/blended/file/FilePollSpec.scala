package blended.file

import java.io.{File, FileOutputStream}

import akka.testkit.TestProbe
import blended.testsupport.TestActorSys
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration._

class FilePollSpec extends FreeSpec with Matchers {

  def genFile(f: File) : Unit = {
    val os = new FileOutputStream(f)
    os.write("Hallo Andreas".getBytes())
    os.flush()
    os.close()
  }

  "The File Poller should" - {

    "do perform a regular poll and process files" in TestActorSys { testkit =>

      implicit val system = testkit.system

      val srcDir = new File(System.getProperty("projectTestOutput") + "/pollspec")
      srcDir.mkdirs()

      val cfg = FilePollConfig(system.settings.config.getConfig("blended.file.poll")).copy(
        sourceDir = srcDir.getAbsolutePath()
      )

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[FileProcessed])

      val actor = system.actorOf(FilePollActor.props(cfg, new SucceedingFileHandler()))

      probe.expectNoMsg(3.seconds)

      val f = new File(srcDir, "test.txt")
      genFile(f)

      probe.expectMsgType[FileProcessed]
      f.exists() should be (false)

      val files = List(
        new File(srcDir, "test.txt"),
        new File(srcDir, "test.xml"),
        new File(srcDir, "test2.txt")
      )

      files.foreach(genFile)

      probe.expectMsgType[FileProcessed]
      probe.expectMsgType[FileProcessed]

      files.forall{ f => (f.getName().endsWith("txt") && !f.exists()) || (!f.getName().endsWith("txt") && f.exists()) } should be (true)

    }

    "block the message processing if a lock file exists in the source directory" in TestActorSys { testkit =>

      implicit val system = testkit.system

      val srcDir = new File(System.getProperty("projectTestOutput") + "/blocking")
      srcDir.mkdirs()

      val cfg = FilePollConfig(system.settings.config.getConfig("blended.file.poll")).copy(
        sourceDir = srcDir.getAbsolutePath(),
        lock = Some("lock")
      )

      val srcFile = new File(srcDir, "test.txt")
      genFile(srcFile)

      val lockFile = new File(srcDir, "lock")
      genFile(lockFile)

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[FileProcessed])

      val actor = system.actorOf(FilePollActor.props(cfg, new SucceedingFileHandler()))

      probe.expectNoMsg(3.seconds)
      srcFile.exists() should be (true)

      lockFile.delete()

      probe.expectMsgType[FileProcessed]
      srcFile.exists() should be (false)
    }
  }
}
