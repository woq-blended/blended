package blended.testsupport.scalatest

import blended.util.logging.Logger
import org.scalatest.Args
import org.scalatest.FreeSpec
import org.scalatest.FreeSpecLike
import org.scalatest.Status
import org.scalatest.Suite

/**
  * Same as [[org.scalatest.FreeSpecLike]] but log the start and the end of each test case to SLF4j in Debug level.
  *
  * @see LoggingFreeSpec
  */
trait LoggingFreeSpecLike extends FreeSpecLike {

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

/**
  * Same as [[org.scalatest.FreeSpec]] but log the start and the end of each test case to SLF4j in Debug level.
  *
  * @see LoggingFreeSpecLike
  */
class LoggingFreeSpec extends FreeSpec with LoggingFreeSpecLike
