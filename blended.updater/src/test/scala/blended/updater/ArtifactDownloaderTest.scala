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
import blended.testsupport.TestFile
import blended.testsupport.TestFile.DeleteWhenNoFailure
import blended.testsupport.TestFile.DeletePolicy
import blended.updater.config.Artifact

class ArtifactDownloaderTest
    extends TestKit(ActorSystem("test"))
    with FreeSpecLike
    with ImplicitSender
    with BeforeAndAfterAll
    with TestFile {

  implicit val deletePolicy: DeletePolicy = DeleteWhenNoFailure

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ArtifactDownloader" - {
    "Download of a local file (without checksum) should work" in {
      val id = nextId()
      withTestFiles("content", "") { (file, target) =>
        assert(Source.fromFile(file).getLines().toList === List("content"), "Precondition failed")
        target.delete()
        val actorRef = system.actorOf(ArtifactDownloader.props())
        actorRef ! ArtifactDownloader.Download(id, Artifact(url = file.toURI().toString()), target)
        fishForMessage() {
          case ArtifactDownloader.DownloadFinished(`id`) => true
        }
        assert(Source.fromFile(target).getLines().toList === List("content"))
      }
    }

    "Download of a missing file should fail" in {
      val id = nextId()
      withTestFiles("content", "") { (file, target) =>
        file.delete()
        target.delete()
        val actorRef = system.actorOf(ArtifactDownloader.props())
        val artifact = Artifact(url = file.toURI().toString())
        actorRef ! ArtifactDownloader.Download(id, artifact, target)
        fishForMessage() {
          case ArtifactDownloader.DownloadFailed(`id`, ex) => true
        }
      }
    }

    "Download of a file into a directory which is actually a file should fail" in {
      pending
    }
  }

}