package blended.mgmt.repo.file

import org.scalatest.FreeSpec

import blended.mgmt.repo.ArtifactRepo
import blended.testsupport.TestFile
import org.scalatest.Matchers
import de.tobiasroeser.lambdatest.TempFile
import java.io.File

class FileArtifactRepoSpec extends FreeSpec with TestFile with Matchers {

  implicit val deletePolicy = TestFile.DeleteWhenNoFailure

  "A file repository" - {
    "should return existing file" in {
      withTestDir() { dir =>
        TempFile.writeToFile(new File(dir, "g/a/1/a-1.jar"), "fake-jar")
        val repo = new FileArtifactRepo("test", dir)
        repo.findFile("g/a/1/a-1.jar") should be(Some(new File(dir, "g/a/1/a-1.jar")))
      }
    }
    "should return SHA1 checksum for existing file" in {
      withTestDir() { dir =>
        TempFile.writeToFile(new File(dir, "g/a/1/a-1.jar"), "fake-jar")
        val repo = new FileArtifactRepo("test", dir)
        repo.findFileSha1Checksum("g/a/1/a-1.jar") should be(Some("badf304cb42fb4c42095e55530bbedc70d990c6e"))
      }
    }
    "should not accept a '..' in the path" in {
      withTestDir() { dir =>
        val jar1 = new File(dir, "g/a/1/a-1.jar")
        TempFile.writeToFile(jar1, "fake-jar")
        val jar2 = new File(dir, "g/a/1/1/a-1.jar")
        TempFile.writeToFile(jar2, "fake-jar")
        val repo = new FileArtifactRepo("test", dir)
        repo.findFile("g/a/1/../1/a-1.jar") should be(Some(jar1))
        repo.findFile("g/a/1/../../a/1/a-1.jar") should be(Some(jar1))
        repo.findFile("g/a/1/../../../g/a/1/a-1.jar") should be(Some(jar1))
//        repo.findFile("g/a/1/../../../../" + dir.getName() + "/g/a/1/a-1.jar") should be(None)
        repo.findFiles("..").toSet shouldBe empty
      }
    }
    "should find all existing files" in {
      withTestDir() { dir =>
        val files = List(
          new File(dir, "g/a/1/a-1.jar"),
          new File(dir, "g/a/2/a-2.jar"),
          new File(dir, "g/b/1/b-1.jar"),
          new File(dir, "c/a/1/a-1.jar")
        )
        files.foreach(f => TempFile.writeToFile(f, "fake-jar"))
        val repo = new FileArtifactRepo("test", dir)
        repo.findFiles("").toSet should be(files.toSet)
        repo.findFiles("/").toSet should be(files.toSet)
      }
    }
  }

}