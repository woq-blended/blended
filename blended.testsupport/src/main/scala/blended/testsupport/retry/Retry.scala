package blended.testsupport.retry

import akka.actor.Scheduler
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object Retry {

  /**
   * Executed and in case of an failure retries an operation `op`. As long as there are retries left, the next retry starts after `delay` times.
   *
   * @param delay The time between a failure and the next retry.
   * @param retries The max count of retries, before giving up.
   * @param onRetry Action to be run before a retry.
   * @param op The operation to be executed and, iff failed, retried.
   * @param ec ExecutionContext to run the inner futures with.
   * @param s The Scheduler used to schedule the next retry.
   *
   * @return The Future containing the result of `op` or the last failure.
   */
  def retry[T](
    delay : FiniteDuration,
    retries : Int,
    onRetry : (Int, Throwable) => Unit = (n, e) => Logger[Retry.type].debug(s"Retrying after failed execution (${n} retries left) : ${e.getMessage()}")
  )(
    op : => T
  )(implicit ec : ExecutionContext, s : Scheduler) : Future[T] =
    Future { op } recoverWith {
      case e : Throwable if retries > 0 => akka.pattern.after(delay, s)({
        onRetry(retries - 1, e)
        retry(delay, retries - 1, onRetry)(op)(ec, s)
      })
    }

  /**
   * Executed and in case of an failure retries an operation `op`. As long as there are retries left, the next retry starts after `delay` times.
   *
   * @param delay The time between a failure and the next retry.
   * @param retries The max count of retries, before giving up.
   * @param onRetry Action to be run before a retry.
   * @param finalDelay The overall delay forwarded to [[Await]], before failing the whole retry block.
   * @param op The operation to be executed and, iff failed, retried.
   * @param ex ExecutionContext to run the inner futures with.
   * @param s The Scheduler used to schedule the next retry.
   *
   * @return The result of `op` or throws an exception.
   */
  def unsafeRetry[T](
    delay : FiniteDuration,
    retries : Int,
    onRetry : (Int, Throwable) => Unit = (n, e) => Logger[Retry.type].debug(s"Retrying after failed execution (${n} retries left) : ${e.getMessage()}"),
    finalDelay : Option[FiniteDuration] = None
  )(
    op : => T
  )(implicit ec : ExecutionContext, s : Scheduler) : T = {
    val res = retry(delay, retries, onRetry)(op)(ec, s)
    Await.result(res, finalDelay.getOrElse(delay * retries + 2.seconds))
  }

}
