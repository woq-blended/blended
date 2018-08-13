package blended.persistence.jdbc

import org.scalatest.BeforeAndAfterEach
import org.scalatest.FreeSpec
import org.scalatest.TestData
import org.scalatest.BeforeAndAfterEachTestData
import blended.util.logging.Logger
import org.scalatest.Args
import org.scalatest.Status
import scala.util.control.NonFatal

trait LoggingFreeSpec extends FreeSpec {

  abstract protected override def runTest(testName: String, args: Args): Status = {
    val log: Logger = Logger[this.type]
    log.info(s"Starting test case: ${testName}")
    try {
      super.runTest(testName, args)
    } finally {
      log.info(s"Finished test case: ${testName}")
    }
  }

}