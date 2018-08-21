package blended.testsupport.scalatest

import blended.util.logging.Logger
import org.scalatest.Args
import org.scalatest.FreeSpec
import org.scalatest.Status

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