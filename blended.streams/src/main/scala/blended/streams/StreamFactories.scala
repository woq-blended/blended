package blended.streams

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.{Done, NotUsed}
import blended.streams.processor.{CollectingActor, Collector}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object StreamFactories {

  def runSourceWithTimeLimit[T](
    name : String,
    source : Source[T, NotUsed],
    timeout : FiniteDuration,
    onCollected : Option[T => Unit] = None,
    completeOn : Option[Seq[T] => Boolean] = None
  )(implicit system : ActorSystem, materializer : Materializer, clazz : ClassTag[T]) : Collector[T] =
    runMatSourceWithTimeLimit(name, source, timeout, onCollected, completeOn)._2

  def runMatSourceWithTimeLimit[T, Mat](
    name : String,
    source : Source[T, Mat],
    timeout : FiniteDuration,
    onCollected : Option[T => Unit] = None,
    completeOn : Option[Seq[T] => Boolean] = None
  )(implicit system : ActorSystem, materializer : Materializer, clazz : ClassTag[T]) : (Mat, Collector[T]) = {

    implicit val eCtxt : ExecutionContext = system.dispatcher
    val stopped = new AtomicBoolean(false)

    val collector = Collector[T](name = name, onCollected = onCollected, completeOn = completeOn)

    val sink = Sink.actorRef(collector.actor, CollectingActor.Completed)

    val ((mat, killswitch), done) = source
      .viaMat(KillSwitches.single)(Keep.both)
      .watchTermination()(Keep.both)
      .toMat(sink)(Keep.left)
      .run()

    done.onComplete {
      case _ => stopped.set(true)
    }

    akka.pattern.after(timeout, system.scheduler) {
      if (!stopped.get()) {
        killswitch.shutdown()
      }
      Future { Done }
    }

    (mat, collector)
  }

  def keepAliveSource[T](bufferSize : Int)(implicit system : ActorSystem, materializer : Materializer) : Source[T, (ActorRef, KillSwitch)] = {

    Source
      .actorRef[T](bufferSize, OverflowStrategy.fail)
      .viaMat(KillSwitches.single)(Keep.both)
  }
}
