package blended.file

import java.io.File

import akka.actor.Props
import akka.testkit.TestProbe
import blended.testsupport.TestActorSys
import blended.util.FileHelper
import org.scalatest.{FreeSpec, Matchers}

class FileDropSpec extends FreeSpec with Matchers {

  "The FileDropActor should" - {

    "drop an uncompressed file into a given directory" in TestActorSys { testkit =>

      implicit val system = testkit.system

      val cmd = FileDropCommand(
        content = "Hallo Andreas".getBytes(),
        directory = System.getProperty("projectTestOutput") + "/drop",
        fileName = "test.txt",
        compressed = false,
        append = false,
        timestamp = System.currentTimeMillis(),
        properties = Map.empty,
        dropNotification = false
      )

      val probe = TestProbe()

      system.actorOf(Props[FileDropActor]).tell(cmd, probe.ref)

      probe.expectMsg(FileDropResult(cmd, true))

      val f : File = new File(cmd.directory, cmd.fileName)

      f.exists() should be (true)

      val content = FileHelper.readFile(f.getAbsolutePath())

      content should be (cmd.content)
    }
  }

}
