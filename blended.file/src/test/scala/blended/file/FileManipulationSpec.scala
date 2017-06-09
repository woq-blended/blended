package blended.file

import java.io.File

import akka.actor.Props
import akka.testkit.TestProbe
import blended.testsupport.TestActorSys
import org.scalatest.{FreeSpec, Matchers}
import org.slf4j.LoggerFactory

class FileManipulationSpec extends FreeSpec with Matchers {

  private[this] val log = LoggerFactory.getLogger(classOf[FileManipulationSpec])

  "The File Manipulation Actor should" - {

    "Allow to delete a file" in TestActorSys { testkit =>

      implicit val system = testkit.system

      val f = new File(System.getProperty("projectTestOutput") + "/files", "toDelete.txt")

      val probe = TestProbe()
      val actor = system.actorOf(Props[FileManipulationActor])

      actor.tell(DeleteFile(f), probe.ref)

      probe.expectMsg(FileCmdResult(DeleteFile(f), true))

      f.exists() should be (false)
    }

    "Allow to rename a file" in TestActorSys { testkit =>

      implicit val system = testkit.system

      val s = new File(System.getProperty("projectTestOutput") + "/files", "toRename.txt")
      val d = new File(System.getProperty("projectTestOutput") + "/files", "newName.txt")

      val probe = TestProbe()
      val actor = system.actorOf(Props[FileManipulationActor])

      actor.tell(RenameFile(s, d), probe.ref)
      probe.expectMsg(FileCmdResult(RenameFile(s, d), true))

      s.exists() should be (false)
      d.exists() should be (true)

    }

    "Fail to rename a file into an existing file" in TestActorSys { testkit =>

      implicit val system = testkit.system

      val s = new File(System.getProperty("projectTestOutput") + "/files", "renameFail.txt")
      val d = new File(System.getProperty("projectTestOutput") + "/files", "AlreadyExists.txt")

      val probe = TestProbe()
      val actor = system.actorOf(Props[FileManipulationActor])

      actor.tell(RenameFile(s, d), probe.ref)

      probe.expectMsg(FileCmdResult(RenameFile(s, d), false))

      s.exists() should be (true)
      d.exists() should be (true)
    }
  }

}
