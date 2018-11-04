package blended.streams.testsupport

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.after
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import blended.streams.processor.{CollectingActor, Collector}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object StreamFactories {

  def runSourceWithTimeLimit[T](
    name : String,
    source : Source[T, NotUsed],
    timeout : FiniteDuration
  )(implicit system : ActorSystem, materializer: ActorMaterializer, clazz : ClassTag[T]) : Collector[T] = {

    implicit val eCtxt = system.dispatcher
    val stopped = new AtomicBoolean(false)

    val collector = Collector[T](name)
    val sink = Sink.actorRef(collector.actor, CollectingActor.Completed)

    val (killswitch, done) = source
      .viaMat(KillSwitches.single)(Keep.right)
      .watchTermination()(Keep.both)
      .toMat(sink)(Keep.left)
      .run()

    done.onComplete {
      case _ => stopped.set(true)
    }

    after(timeout, system.scheduler){
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

  def keepAliveFlow[T](
    flow : Flow[T, _, _],
    msgs: T*
  )(implicit system: ActorSystem, materializer: Materializer, ectxt: ExecutionContext) : KillSwitch = {

    val source = Source.actorRef[T](msgs.size, OverflowStrategy.fail)

    val (actor : ActorRef, killswitch : KillSwitch) = source
      .viaMat(flow)(Keep.left)
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(Sink.ignore)(Keep.left)
      .run()

    msgs.foreach(m => actor ! m)

    killswitch
  }
}
