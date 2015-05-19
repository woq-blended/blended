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

class BlockingDownloaderTest
  extends TestKit(ActorSystem("test"))
  with FreeSpecLike
  with ImplicitSender
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  def withTestFile(content: String)(f: File => Any): Unit = {
    val file = File.createTempFile("test", "")
    val os = new PrintStream(new FileOutputStream(file))
    os.print(content)
    os.close()
    f(file)
    if (!file.delete()) {
      file.deleteOnExit()
    }
  }

  "Reference" - {
    "Download a local file" in {
      withTestFile("content") { file =>
        withTestFile("") { target =>

import sys.process._
          file.#>(target).!
          val downloadedContent = Source.fromFile(target).getLines().mkString("\n")
          assert("content" === downloadedContent)
        }
      }
    }
  }

  "DownloadActor" - {
    "Download of a local file should work" in {
      withTestFile("content") { file =>
        withTestFile("") { target =>
          val actorRef = system.actorOf(BlockingDownloader.props())
          actorRef ! BlockingDownloader.Download(testActor, file.toURI().toString(), target)
          val msg = expectMsgType[BlockingDownloader.DownloadResult]
          assert(msg.url === file.toURI().toString())
          assert(msg.file.isSuccess)
          val downloadedContent = Source.fromFile(target).getLines().mkString("\n")
          assert("content" === downloadedContent)
        }
      }
    }

    "Download of a missing file should fail" in {
      withTestFile("content") { file =>
        file.delete()
        withTestFile("") { target =>
          val actorRef = system.actorOf(BlockingDownloader.props())
          actorRef ! BlockingDownloader.Download(testActor, file.toURI().toString(), target)
          val msg = expectMsgPF() {
            case BlockingDownloader.DownloadResult(msg, Failure(ex)) => (msg, ex)
          }
          assert(msg._1 === file.toURI().toString())
        }
      }
    }

    "Download of a file into a directory with is actually a file should fail" in {
      pending
    }
  }

}