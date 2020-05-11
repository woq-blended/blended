package blended.updater

import java.io.{File, PrintStream}

import akka.actor.{ActorSystem, actorRef2Scala}
import akka.testkit.{ImplicitSender, TestKit}
import blended.testsupport.TestFile
import blended.testsupport.TestFile.{DeletePolicy, DeleteWhenNoFailure}
import blended.updater.config.Artifact
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpecLike
import scala.io.Source

class ArtifactDownloaderTest
  extends TestKit(ActorSystem("test"))
  with AnyFreeSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with TestFile {

  implicit val deletePolicy : DeletePolicy = DeleteWhenNoFailure

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

    "Download of a local file (without checksum) with Maven coordinates should work" in {
      val id = nextId()
      withTestDir(new File("target/tmp")) { dir =>
        val mvnRepo = dir.toURI().toString()
        val sourceFile = new File(dir, s"g1/g2/art/1/art-1.jar")
        sourceFile.getParentFile().mkdirs()
        val ps = new PrintStream(sourceFile)
        ps.print("content")
        ps.flush()
        ps.close()
        assert(sourceFile.exists() === true)

        val mvnUrl = "mvn:g1.g2:art:1"
        val target = new File(dir, "target.jar")

        val actorRef = system.actorOf(ArtifactDownloader.props(List(mvnRepo)))
        actorRef ! ArtifactDownloader.Download(id, Artifact(url = mvnUrl), target)
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
          case ArtifactDownloader.DownloadFailed(`id`, _) => true
        }
      }
    }

    "Download of a file into a directory which is actually a file should fail" in {
      pending
    }
  }

}
