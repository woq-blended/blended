package blended.updater.internal

import org.scalatest.FreeSpec
import java.io.File
import scala.io.Source
import scala.util.Failure
import scala.util.Failure
import blended.testsupport.TestFile

class UnzipperTest extends FreeSpec
    with TestFile {

  implicit val deletePolicy = TestFile.DeleteWhenNoFailure

  val testZip = new File("src/test/binaryResources/test.zip")
  val test2Zip = new File("src/test/binaryResources/test2.zip")

  "test environment" - {
    "should contain a usable test.zip file" in {
      assert(testZip.exists() === true)
    }
  }

  "With a usable test.zip file" - {
    "unpacking everything should work" in {
      withTestDir() { dir =>
        val files = Unzipper.unzip(testZip, dir, Nil, None, None)
        assert(listFilesRecursive(dir).toSet === Set("dir1", "dir1/dir1a", "dir1/file2a.txt", "dir2", "file1.txt"))
      }
    }

    "unpacking with blacklist 1 should work" in {
      withTestDir() { dir =>
        val blacklist = List("dir1", "file1.txt")

        val fileSelector = { fileName: String => !blacklist.exists(b => fileName == b || fileName.startsWith(b + "/")) }
        assert(fileSelector("dir2") === true)
        assert(fileSelector("dir1") === false)
        assert(fileSelector("dir1/file1") === false)

        val files = Unzipper.unzip(testZip, dir, Nil, Some(fileSelector), None)
        println("unpacked files: " + files.get)
        assert(listFilesRecursive(dir).toSet === Set("dir2"))
      }
    }
    "unpacking with blacklist 2 should work" in {
      withTestDir() { dir =>
        val blacklist = List("dir1/dir1a")

        val fileSelector = { fileName: String => !blacklist.exists(b => fileName == b || fileName.startsWith(b + "/")) }

        val files = Unzipper.unzip(testZip, dir, Nil, Some(fileSelector), None)
        println("unpacked files: " + files.get)
        assert(listFilesRecursive(dir).toSet === Set("dir1", "dir1/file2a.txt", "dir2", "file1.txt"))
      }
    }

  }

  "With a usable test2.zip file" - {
    "unpacking everything and replacing a varible should work" in {
      withTestDir() { dir =>
        val files = Unzipper.unzip(test2Zip, dir, Nil, None, Some(Unzipper.PlaceholderConfig(
          openSequence = "${", closeSequence = "}", escapeChar = '\\', properties = Map("VERSION" -> "1.0.0"),
          failOnMissing = true
        )))
        println("unpacked files: " + files)
        assert(listFilesRecursive(dir).toSet === Set("etc", "etc/test.conf"))
        assert(Source.fromFile(new File(dir, "etc/test.conf")).getLines().toList === List("name = \"replaced-version-1.0.0\"", ""))
      }
    }
    "unpacking everything and replacing a missing varible should not work" in {
      withTestDir() { dir =>
        val files = Unzipper.unzip(test2Zip, dir, Nil, None, Some(Unzipper.PlaceholderConfig(
          openSequence = "${", closeSequence = "}", escapeChar = '\\', properties = Map(),
          failOnMissing = true
        )))
        println("unpacked files: " + files)
        assert(files.isFailure)
        assert(files.isInstanceOf[Failure[_]])
        assert(files.asInstanceOf[Failure[_]].exception.getMessage.contains("Could not unzip file"))
        assert(files.asInstanceOf[Failure[_]].exception.getCause.getMessage() === "No property found to replace: ${VERSION}")
      }
    }
    "unpacking everything and replacing a missing varible should work when failOnMissing is false" in {
      withTestDir() { dir =>
        val files = Unzipper.unzip(test2Zip, dir, Nil, None, Some(Unzipper.PlaceholderConfig(
          openSequence = "${", closeSequence = "}", escapeChar = '\\', properties = Map(),
          failOnMissing = false
        )))
        println("unpacked files: " + files)
        assert(listFilesRecursive(dir).toSet === Set("etc", "etc/test.conf"))
        assert(Source.fromFile(new File(dir, "etc/test.conf")).getLines().toList === List("name = \"replaced-version-${VERSION}\"", ""))
      }
    }

  }

}