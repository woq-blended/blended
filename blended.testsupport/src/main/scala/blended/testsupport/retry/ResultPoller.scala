package blended.testsupport.retry

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorSystem, Scheduler}
import blended.util.logging.Logger

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class ResultPoller[T](
  system : ActorSystem,
  timeout : FiniteDuration,
  hint : String
)(op : () => Future[T]) {

  private val pollInterval : FiniteDuration = 100.millis
  private val retries : Int = Math.max(timeout / pollInterval, 1).floor.toInt

  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val scheduler : Scheduler = system.scheduler

  private val running : AtomicBoolean = new AtomicBoolean(false)
  private var result : Option[T] = None

  private def run(verify : T => Unit) : Future[Try[T]] = {
    def singlePoll : Try[T] = {

      if (result.isEmpty && !running.compareAndSet(false, true)) {
        op().onComplete {
          case Success(v) =>
            running.set(false)
            result = Some(v)
          case Failure(_) =>
            running.set(false)
        }
      }

      if (running.get()) {
        throw new Exception("Currently executing")
      } else {
        result match {
          case None => throw new Exception("No result yet")
          case Some(v) =>
            Try { verify(v) } match {
              case Failure(e) => throw e
              case Success(_) => Success(v)
            }
        }
      }
    }

    Retry.retry(
      delay = pollInterval,
      retries = retries,
      onRetry = (n, e) => Logger(classOf[ResultPoller[_]].getClass().getName()).info(s"ResultPoller [$hint] : ${e.getMessage()}, [$n] retries left")
    )(singlePoll)
  }

  def execute(f : T => Unit) : Try[T] = { Await.result(run(f), timeout + 1.second) }
}
