package blended.streams

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import blended.streams.processor.{CollectingActor, Collector}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

object StreamFactories {

  def runSourceWithTimeLimit[T](
    name : String,
    source : Source[T, NotUsed],
    timeout : FiniteDuration
  )(collected : T => Unit)(implicit system : ActorSystem, materializer: Materializer, clazz : ClassTag[T]) : Collector[T] = {

    implicit val eCtxt : ExecutionContext = system.dispatcher
    val stopped = new AtomicBoolean(false)

    val collector = Collector[T](name)(collected)
    val sink = Sink.actorRef(collector.actor, CollectingActor.Completed)

    val (killswitch, done) = source
      .viaMat(KillSwitches.single)(Keep.right)
      .watchTermination()(Keep.both)
      .toMat(sink)(Keep.left)
      .run()

    done.onComplete {
      case Success(c) =>
        stopped.set(true)
      case Failure(t) =>
        collector.actor ! CollectingActor.Completed
        stopped.set(true)
    }

    akka.pattern.after(timeout, system.scheduler){
      if (!stopped.get()) {
        killswitch.shutdown()
      }
      Future { Done }
    }

    collector
  }

  def keepAliveSource[T](bufferSize : Int)(implicit system: ActorSystem, materializer: Materializer) : Source[T, (ActorRef, KillSwitch)] = {

    Source
      .actorRef[T](bufferSize, OverflowStrategy.fail)
      .viaMat(KillSwitches.single)(Keep.both)
  }
}
