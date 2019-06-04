package blended.mgmt.repo.file

import java.io.{ByteArrayInputStream, File}

import blended.testsupport.TestFile
import de.tobiasroeser.lambdatest.TempFile
import org.scalatest.{FreeSpec, Matchers}

import scala.util.Success

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
        repo.listFiles("..").toSet shouldBe empty
      }
    }
    "should list all existing files" in {
      withTestDir() { dir =>
        val files = List(
          "g/a/1/a-1.jar",
          "g/a/2/a-2.jar",
          "g/b/1/b-1.jar",
          "c/a/1/a-1.jar"
        )
        files.foreach(f => TempFile.writeToFile(new File(dir, f), "fake-jar"))
        val repo = new FileArtifactRepo("test", dir)
        repo.listFiles("").toSet should be(files.toSet)
        repo.listFiles("/").toSet should be(files.toSet)
      }
    }
  }

  "A writable artifact repo" - {

    "should upload a file without checksum" in {
      withTestDir() { dir =>
        val path = "p1/p2/f1"
        val repo = new FileArtifactRepo("test", dir)
        val baip = new ByteArrayInputStream("content".getBytes())
        try {
          repo.uploadFile(path, baip, None)
        } finally {
          baip.close()
        }
        repo.findFile(path) should be(Some(new File(dir, path)))
      }
    }

    "should fail when uploading a file twice without checksum" in {
      withTestDir() { dir =>
        val path = "p1/p2/f1"
        val repo = new FileArtifactRepo("test", dir)
        // first upload
        val baip = new ByteArrayInputStream("test".getBytes())
        try {
          repo.uploadFile(path, baip, None) should be(Success[Unit]())
        } finally {
          baip.close()
        }
        repo.findFile(path) should be(Some(new File(dir, path)))
        // second upload
        val baip2 = new ByteArrayInputStream("test".getBytes())
        try {
          val fail = repo.uploadFile(path, baip2, None) should be
        } finally {
          baip.close()
        }
      }
    }

    "should accept a second upload of the same file, when the checksum matches" in {
      pending
    }
  }

}
