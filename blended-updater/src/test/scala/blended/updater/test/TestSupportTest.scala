package blended.updater.test

import org.scalatest.FreeSpec
import java.io.File
import scala.io.Source
import blended.updater.test.TestSupport.DeleteAlways
import blended.updater.test.TestSupport.DeleteNever
import blended.updater.test.TestSupport.DeleteWhenNoFailure
import blended.updater.test.TestSupport.DeletePolicy

class TestSupportTest extends FreeSpec with TestSupport {

  "withTestFile" - {

    "should create a file" in {
      withTestFile("content") { file =>
        assert(file.exists())
      }(DeleteAlways)
    }

    {
      def testDelete(deleteWhenFailure: Boolean, deleteWhenNoFailure: Boolean)(implicit deletePolicy: DeletePolicy) = {
        def delete(d: Boolean) = if (d) "delete" else "not delete"
        s"shold ${delete(deleteWhenNoFailure)} when no failure" in {
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
        s"shold ${delete(deleteWhenFailure)} when failure" in {
          var f: File = null
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