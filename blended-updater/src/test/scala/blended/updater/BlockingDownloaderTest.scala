package blended.updater

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import scala.io.Source
import scala.sys.process.fileToProcess
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.FreeSpecLike
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import scala.util.Failure
import java.io.FileNotFoundException
import java.util.UUID
import blended.updater.test.TestSupport
import blended.updater.test.TestSupport.DeleteWhenNoFailure
import blended.updater.test.TestSupport.DeletePolicy

class BlockingDownloaderTest
    extends TestKit(ActorSystem("test"))
    with FreeSpecLike
    with ImplicitSender
    with BeforeAndAfterAll
    with TestSupport {

  implicit val deletePolicy: DeletePolicy = DeleteWhenNoFailure

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Reference" - {
    "Download a local file" in {
      withTestFiles("content", "") { (file, target) =>
        import sys.process._
        file.#>(target).!
        val downloadedContent = Source.fromFile(target).getLines().mkString("\n")
        assert("content" === downloadedContent)
      }
    }
  }

  "DownloadActor" - {
    "Download of a local file should work" in {
      val id = nextId()
      withTestFiles("content", "") { (file, target) =>
        val actorRef = system.actorOf(BlockingDownloader.props())
        actorRef ! BlockingDownloader.Download(id, testActor, file.toURI().toString(), target)
        val msg = expectMsgType[BlockingDownloader.DownloadFinished]
        assert(msg.url === file.toURI().toString())
        assert(msg.file === target)
        val downloadedContent = Source.fromFile(target).getLines().mkString("\n")
        assert("content" === downloadedContent)
      }
    }

    "Download of a missing file should fail" in {
      val id = nextId()
      withTestFiles("content", "") { (file, target) =>
        file.delete()
        val actorRef = system.actorOf(BlockingDownloader.props())
        actorRef ! BlockingDownloader.Download(id, testActor, file.toURI().toString(), target)
        val msg = expectMsgPF() {
          case BlockingDownloader.DownloadFailed(id, msg, file, ex) => (msg, ex)
        }
        assert(msg._1 === file.toURI().toString())
      }
    }

    "Download of a file into a directory which is actually a file should fail" in {
      pending
    }
  }

}