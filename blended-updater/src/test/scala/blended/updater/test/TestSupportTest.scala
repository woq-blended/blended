package blended.updater.test

import org.scalatest.FreeSpec
import java.io.File
import scala.io.Source

class TestSupportTest extends FreeSpec with TestSupport {

  "withTestFile" - {

    "should create a file" in {
      withTestFile("content") { file =>
        assert(file.exists())
      }
    }

    "shold delete a file afterwards" in {
      val f = withTestFile("content") { file =>
        file
      }
      assert(!f.exists())
    }

    "should create a file with correct content" in {
      withTestFile("correct\ncontent") { file =>
        val content = Source.fromFile(file).getLines().mkString("\n")
        assert(content === "correct\ncontent")
      }
    }
  }

}