package blended.testsupport.retry

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorSystem, Scheduler}
import blended.util.logging.Logger

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class ResultPoller[T](system : ActorSystem, timeout : FiniteDuration)(op : () => Future[T]) {

  private val log : Logger = Logger(getClass().getName())

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
          case Failure(t) =>
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
              case s @ Success(_) => Success(v)
            }
        }
      }
    }

    Retry.retry(pollInterval, retries)(singlePoll)
  }

  def execute(f : T => Unit) : Try[T] = { Await.result(run(f), timeout + 1.second) }
}
