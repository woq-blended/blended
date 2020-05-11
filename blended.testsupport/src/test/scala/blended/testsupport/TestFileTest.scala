package blended.testsupport

import java.io.File

import blended.testsupport.TestFile.{DeleteAlways, DeleteNever, DeletePolicy, DeleteWhenNoFailure}
import org.scalatest.freespec.AnyFreeSpec

import scala.io.Source

class TestFileTest extends AnyFreeSpec with TestFile {

  "withTestFile" - {

    "should create a file" in {
      withTestFile("content") { file =>
        assert(file.exists())
      }(DeleteAlways)
    }

    {
      def testDelete(deleteWhenFailure : Boolean, deleteWhenNoFailure : Boolean)(implicit deletePolicy : DeletePolicy) = {
        def delete(d : Boolean) = if (d) "delete" else "not delete"
        s"should ${delete(deleteWhenNoFailure)} when no failure" in {
          val f = withTestFile("content") { file =>
            assert(file.exists())
            file
          }
          try {
            assert(f.exists() != deleteWhenNoFailure)
          } finally {
            f.delete()
          }
        }
        s"should ${delete(deleteWhenFailure)} when failure" in {
          var f : File = null
          intercept[Exception] {
            withTestFile("content") { file =>
              assert(file.exists())
              f = file
              throw new Exception("failure")
            }
          }
          try {
            assert(f.exists() != deleteWhenFailure)
          } finally {
            f.delete()
          }
        }

      }

      "when DeletePolicy is " + DeleteNever - {
        testDelete(false, false)(DeleteNever)
      }

      "when DeletePolicy is " + DeleteWhenNoFailure - {
        testDelete(false, true)(DeleteWhenNoFailure)
      }

      "when DeletePolicy is " + DeleteAlways - {
        testDelete(true, true)(DeleteAlways)
      }
    }

    "should create a file with correct content" in {
      withTestFile("correct\ncontent") { file =>
        val content = Source.fromFile(file).getLines().mkString("\n")
        assert(content === "correct\ncontent")
      }(DeleteAlways)
    }
  }

}
