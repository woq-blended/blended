package blended.testsupport.scalatest

import scala.util.control.NonFatal

import blended.util.logging.Logger
import org.scalatest.Args
import org.scalatest.FreeSpec
import org.scalatest.FreeSpecLike
import org.scalatest.Status

/**
 * Same as [[org.scalatest.FreeSpecLike]] but log the start and the end of each test case to SLF4j in Debug level.
 *
 * If you want also see the last thrown exception of a test case (this is most probably a failed assertion),
 * you can use [[LoggingFreeSpec.logException]].
 *
 * Example:
 * {{{
 *   "A failing test (that also appears in log file)" in logException {
 *     assert(1 == 0)
 *   }
 * }}}
 *
 * @see LoggingFreeSpec
 */
trait LoggingFreeSpecLike extends FreeSpecLike {

  abstract protected override def runTest(testName: String, args: Args): Status = {
    val log: Logger = Logger[this.type]
    log.info(s"Starting test case: ${testName}")
    var reported = false
    try {
      val status = super.runTest(testName, args)
      log.info(s"${if (status.succeeds()) "Finished" else "Failed"} test case: ${testName}\n" + ("-" *80))
      reported = true
      status
    } finally {
      if (!reported) log.info(s"Finished test case: ${testName}" + ("-" *80))
    }
  }

  def logException[T](f: => T): T = try {
    f
  } catch {
    case NonFatal(e) =>
      Logger[this.type].error(e)("Exception caught")
      throw e
  }

}

/**
 * Same as [[org.scalatest.FreeSpec]] but with mix-in of [LoggingFreeSpecLike]],
 * which logs the start and the end of each test case.
 *
 * @see LoggingFreeSpecLike
 */
class LoggingFreeSpec extends FreeSpec with LoggingFreeSpecLike
