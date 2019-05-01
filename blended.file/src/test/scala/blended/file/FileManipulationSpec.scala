package blended.file

import java.io.File

import akka.actor.Props
import akka.testkit.TestProbe
import blended.testsupport.TestActorSys
import blended.util.logging.Logger
import org.scalatest.{ FreeSpec, Matchers }
import scala.concurrent.duration._

class FileManipulationSpec extends FreeSpec with Matchers {

  private[this] val log = Logger[FileManipulationSpec]

  "The File Manipulation Actor should" - {

    "Allow to delete a file" in TestActorSys { testkit =>

      implicit val system = testkit.system

      val f = new File(System.getProperty("projectTestOutput") + "/files", "toDelete.txt")

      val probe = TestProbe()
      val actor = system.actorOf(Props[FileManipulationActor])

      actor.tell(DeleteFile(f), probe.ref)

      probe.expectMsg(FileCmdResult(DeleteFile(f), None))

      f.exists() should be (false)
    }

    "Allow to rename a file" in TestActorSys { testkit =>

      implicit val system = testkit.system

      val s = new File(System.getProperty("projectTestOutput") + "/files", "toRename.txt")
      val d = new File(System.getProperty("projectTestOutput") + "/files", "newName.txt")
      if (d.exists()) d.delete()

      val probe = TestProbe()
      val actor = system.actorOf(Props[FileManipulationActor])

      actor.tell(RenameFile(s, d), probe.ref)
      probe.expectMsg(FileCmdResult(RenameFile(s, d), None))

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

      probe.fishForMessage(1.second){
        case FileCmdResult(RenameFile(_,_), Some(t)) =>
          t.isInstanceOf[FileCommandTimeoutException]
      }

      s.exists() should be (true)
      d.exists() should be (true)
    }
  }

}
