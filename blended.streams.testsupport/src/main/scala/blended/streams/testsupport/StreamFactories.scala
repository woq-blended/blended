package blended.streams.testsupport

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.after
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.TestProbe
import akka.{Done, NotUsed}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object StreamFactories {

  def runSourceWithTimeLimit[T](
    name : String,
    source : Source[T, NotUsed],
    timeout : FiniteDuration
  )(implicit system : ActorSystem, materializer: ActorMaterializer, clazz : ClassTag[T]) : List[T] = {

    implicit val eCtxt = system.dispatcher
    val stopped = new AtomicBoolean(false)

    val receiveProbe = TestProbe()
    val receiver = system.actorOf(CollectingActor.props[T](name, receiveProbe.ref))
    val sink = Sink.actorRef(receiver, CollectingActor.Completed)

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

    receiveProbe.expectMsgType[List[T]](timeout + 1.second)
  }

  def sendAndKeepAlive[T](
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
