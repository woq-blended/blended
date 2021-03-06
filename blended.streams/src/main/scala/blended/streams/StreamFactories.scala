package blended.streams

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.{Done, NotUsed}
import blended.streams.processor.{CollectingActor, Collector}
import blended.util.logging.Logger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

object StreamFactories {

  private val log: Logger = Logger[StreamFactories.type]

  def runSourceWithTimeLimit[T](
    name: String,
    source: Source[T, NotUsed],
    timeout: Option[FiniteDuration],
    onCollected: Option[T => Boolean] = None,
    completeOn: Option[Seq[T] => Boolean] = None
  )(implicit system: ActorSystem, clazz: ClassTag[T]): Collector[T] =
    runMatSourceWithTimeLimit(name, source, timeout, onCollected, completeOn)._2

  def runMatSourceWithTimeLimit[T, Mat](
    name: String,
    source: Source[T, Mat],
    timeout: Option[FiniteDuration],
    onCollected: Option[T => Boolean] = None,
    completeOn: Option[Seq[T] => Boolean] = None
  )(implicit system: ActorSystem, clazz: ClassTag[T]): (Mat, Collector[T]) = {

    implicit val eCtxt: ExecutionContext = system.dispatcher

    val stopped = new AtomicBoolean(false)

    val collector = Collector[T](name = name, onCollected = onCollected, completeOn = completeOn)

    val sink = Sink.actorRef(collector.actor, CollectingActor.Success, t => CollectingActor.Failed(t))

    val ((mat, killswitch), done) = source
      .viaMat(KillSwitches.single)(Keep.both)
      .watchTermination()(Keep.both)
      .toMat(sink)(Keep.left)
      .run()

    done.onComplete {
      case Success(c) =>
        stopped.set(true)
      case Failure(t) =>
        collector.actor ! CollectingActor.Success
        stopped.set(true)
    }

    timeout.foreach { t =>
      akka.pattern.after(t, system.scheduler) {
        if (!stopped.get()) {
          log.info(s"Stopping collector [$name] after [${timeout}]")
          killswitch.shutdown()
        }
        Future { Done }
      }
    }

    (mat, collector)
  }

  def actorSource[T](
    bufferSize: Int,
    overflowStrategy: OverflowStrategy = OverflowStrategy.fail
  ): Source[T, ActorRef] = {

    val complete: PartialFunction[Any, CompletionStrategy] = {
      case akka.actor.Status.Success(s: CompletionStrategy) => s
      case akka.actor.Status.Success(_)                     => CompletionStrategy.draining
      case akka.actor.Status.Success                        => CompletionStrategy.draining
    }

    val fail: PartialFunction[Any, Throwable] = {
      case akka.actor.Status.Failure(t) => t
    }

    Source.actorRef[T](complete, fail, bufferSize, overflowStrategy)
  }

  def keepAliveSource[T](bufferSize: Int): Source[T, (ActorRef, KillSwitch)] =
    actorSource[T](bufferSize).viaMat(KillSwitches.single)(Keep.both)
}
