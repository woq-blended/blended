package blended.streams.worklist

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches, Materializer, OverflowStrategy}
import akka.testkit.TestKit
import blended.streams.FlowHeaderConfig
import blended.streams.message.FlowEnvelopeLogger
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

case class DummyItem(
  id : String
) extends WorklistItem

class WorklistSpec extends TestKit(ActorSystem("Worklist"))
  with LoggingFreeSpecLike
  with Matchers {

  private val log = Logger[WorklistSpec]
  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create("App")
  private val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerCfg, log)
  private implicit val materialzer : Materializer = ActorMaterializer()
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private val defaultCooldown = 500.millis

  def worklist(items : WorklistItem*) = Worklist("test", items)

  private def withWorklistManager(events : WorklistEvent*)(f : Seq[WorklistEvent] => Unit) : Unit =
    withWorklistManager(defaultCooldown, events:_*)(f)

  private def withWorklistManager(cooldown: FiniteDuration, events : WorklistEvent*)(f : Seq[WorklistEvent] => Unit) : Unit = {

    val source = Source.actorRef(100, OverflowStrategy.dropBuffer)

    val sink = Sink.seq[WorklistEvent]

    val mgr : Flow[WorklistEvent, WorklistEvent, NotUsed] = WorklistManager.flow("worklist", envLogger)

    val ((actor, killswitch), result) = source
      .viaMat(KillSwitches.single)(Keep.both)
      .viaMat(Flow.fromGraph(mgr))(Keep.left)
      .toMat(sink)(Keep.both)
      .run()

    akka.pattern.after(cooldown, system.scheduler) {
      Future {
        killswitch.shutdown()
      }
    }

    events.foreach(e => actor ! e)

    f(Await.result(result, cooldown * 2))
  }

  "The worklist manager should" - {

    "Generate a Started worklist event once the worklist is kicked off" in {

      val wl = Worklist("test", Seq(DummyItem("item")))

      withWorklistManager(1.second, WorklistStarted(wl)) { r =>
        r should have size(2)
        assert(r.head.isInstanceOf[WorklistStarted])
        assert(r.last.isInstanceOf[WorklistTerminated])
      }
    }

    "Ignore a start event for a worklist that is already started" in {

      val wl = Worklist("test", Seq(DummyItem("item")))

      withWorklistManager(WorklistStarted(wl), WorklistStarted(wl)) { r =>
        r should have size(2)
        assert(r.head.isInstanceOf[WorklistStarted])
        assert(r.last.isInstanceOf[WorklistTerminated])
      }
    }

    "Create a Worklist Terminated event with state [completed] once all items are completed" in {

      val item1 = DummyItem("item1")
      val item2 = DummyItem("item2")

      withWorklistManager(
        WorklistStarted(worklist(item1)),
        WorklistStepCompleted(worklist(item2), WorklistStateCompleted),
        WorklistStepCompleted(worklist(item1), WorklistStateCompleted),
      ) { r =>

        r should have size(2)

        assert(r.head.isInstanceOf[WorklistStarted])
        assert(r.last.isInstanceOf[WorklistTerminated])
      }

      withWorklistManager(
        WorklistStarted(worklist(item1, item2)),
        WorklistStepCompleted(worklist(item2), WorklistStateCompleted),
        WorklistStepCompleted(worklist(item1), WorklistStateCompleted),
      ) { r =>

        r should have size(2)

        assert(r.head.isInstanceOf[WorklistStarted])

        r.last match {
          case t : WorklistTerminated =>
            t.state should be (WorklistStateCompleted)
          case _ => fail()
        }
      }
    }

    "Send a Terminated event with state [timeOut] once a worklist item has timed out" in {

      val item1 = DummyItem("item1")
      val item2 = DummyItem("item2")

      withWorklistManager(
        WorklistStarted(worklist(item1, item2)),
      ) { r =>

        r should have size(2)

        assert(r.head.isInstanceOf[WorklistStarted])
        r.last match {
          case t : WorklistTerminated =>
            t.state should be (WorklistStateTimeout)
          case _ => fail()
        }
      }
    }
  }

}
