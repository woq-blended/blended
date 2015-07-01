package blended.updater.internal

import org.scalatest.FreeSpec
import java.io.File
import blended.updater.test.TestSupport

class UtilTest extends FreeSpec
    with TestSupport {

  implicit val deletePolicy = TestSupport.DeleteWhenNoFailure

  val testZip = new File("src/test/binaryResources/test.zip")

  "test environment" - {
    "should contain a usable test.zip file" in {
      assert(testZip.exists() === true)
    }
  }

  "With a usable test.zip file" - {
    "unpacking everything should work" in {
      withTestDir() { dir =>
        val files = Util.unzip(testZip, dir, Nil, None)
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

        val files = Util.unzip(testZip, dir, Nil, Some(fileSelector))
        println("unpacked files: " + files.get)
        assert(listFilesRecursive(dir).toSet === Set("dir2"))
      }
    }
    "unpacking with blacklist 2 should work" in {
      withTestDir() { dir =>
        val blacklist = List("dir1/dir1a")

        val fileSelector = { fileName: String => !blacklist.exists(b => fileName == b || fileName.startsWith(b + "/")) }

        val files = Util.unzip(testZip, dir, Nil, Some(fileSelector))
        println("unpacked files: " + files.get)
        assert(listFilesRecursive(dir).toSet === Set("dir1", "dir1/file2a.txt", "dir2", "file1.txt"))
      }
    }

  }

}