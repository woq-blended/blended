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

object StreamFactories {

  private val log : Logger = Logger[StreamFactories.type]

  def runSourceWithTimeLimit[T](
    name : String,
    source : Source[T, NotUsed],
    timeout : Option[FiniteDuration],
    onCollected : Option[T => Unit] = None,
    completeOn : Option[Seq[T] => Boolean] = None
  )(implicit system : ActorSystem, clazz : ClassTag[T]) : Collector[T] =
    runMatSourceWithTimeLimit(name, source, timeout, onCollected, completeOn)._2

  def runMatSourceWithTimeLimit[T, Mat](
    name : String,
    source : Source[T, Mat],
    timeout : Option[FiniteDuration],
    onCollected : Option[T => Unit] = None,
    completeOn : Option[Seq[T] => Boolean] = None
  )(implicit system : ActorSystem, clazz : ClassTag[T]) : (Mat, Collector[T]) = {

    implicit val materializer : Materializer = ActorMaterializer()(system)
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

    timeout.foreach{ t =>
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

  def keepAliveSource[T](bufferSize : Int): Source[T, (ActorRef, KillSwitch)] = {
    Source
      .actorRef[T](bufferSize, OverflowStrategy.fail)
      .viaMat(KillSwitches.single)(Keep.both)
  }
}
