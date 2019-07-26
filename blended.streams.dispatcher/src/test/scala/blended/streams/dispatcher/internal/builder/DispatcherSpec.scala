package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Source}
import blended.streams.message.FlowEnvelope
import blended.streams.processor.Collector
import blended.streams.transaction._
import blended.streams.worklist._
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class DispatcherSpec extends DispatcherSpecSupport
  with Matchers {

  override def loggerName : String = classOf[DispatcherSpec].getName()
  private val goodSend = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env => env }
  private val defaultBufferSize : Int = 10

  private def runDispatcher(
    ctxt : DispatcherExecContext,
    send : Flow[FlowEnvelope, FlowEnvelope, NotUsed]
  ) : (ActorRef, KillSwitch, Collector[FlowTransactionEvent]) = {

    implicit val system : ActorSystem = ctxt.system
    implicit val materializer : Materializer = ActorMaterializer()

    val transColl = Collector[FlowTransactionEvent]("trans")(_ => {})

    val source = Source.actorRef[FlowEnvelope](defaultBufferSize, OverflowStrategy.fail)

    val sinkGraph = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val dispatcher = b.add(DispatcherBuilder(ctxt.idSvc, ctxt.cfg, send)(ctxt.bs).dispatcher())
      val out = b.add(transColl.sink)

      dispatcher ~> out

      SinkShape(dispatcher.in)
    }

    val (actor, killswitch) = source
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(sinkGraph)(Keep.left)
      .run()

    (actor, killswitch, transColl)
  }

  private def runTest[T](testMsg : DispatcherExecContext => Seq[FlowEnvelope])(f : List[FlowTransactionEvent] => T) : Future[T] = {
    withDispatcherConfig { ctxt =>
      implicit val eCtxt : ExecutionContext = ctxt.system.dispatcher

      val (actor, killswitch, coll) = runDispatcher(ctxt, goodSend)

      akka.pattern.after(500.millis, ctxt.system.scheduler)(Future { killswitch.shutdown() })

      testMsg(ctxt).foreach(env => actor ! env)

      coll.result.map(l => f(l))
    }
  }

  "The Dispatcher should" - {

    "produce a transaction failed event in case the flow fails with an exception" in {

      val testMsgs : DispatcherExecContext => Seq[FlowEnvelope] = ctxt => Seq(
        FlowEnvelope(),
        FlowEnvelope().withHeader(ctxt.bs.headerResourceType, "Dummy").get,
        FlowEnvelope().withHeader(ctxt.bs.headerResourceType, "NoOutbound").get
      )

      runTest(testMsgs) { events =>
        events should have size 3
        assert(events.forall(_.state == FlowTransactionStateFailed))
      }
    }

    "produce a transaction update event for the started worklist if the envelope is only routed externally" in {

      val testMsgs : DispatcherExecContext => Seq[FlowEnvelope] = ctxt => Seq(
        FlowEnvelope().withHeader(ctxt.bs.headerResourceType, "NoCbe").get
      )

      runTest(testMsgs) { events =>
        events should have size 1
        events.foreach { e =>
          val event = e.asInstanceOf[FlowTransactionUpdate]
          event.state should be (FlowTransactionStateUpdated)
          event.updatedState should be (WorklistStateStarted)
        }
      }
    }

    "produce a transaction update event for the started worklist and one transaction update for each outbound flow that is routed internal" in {
      val testMsgs : DispatcherExecContext => Seq[FlowEnvelope] = ctxt => Seq(
        FlowEnvelope().withHeader(ctxt.bs.headerResourceType, "FanOut").get
      )

      runTest(testMsgs) { events =>
        events should have size 2

        assert(events.forall(_.state == FlowTransactionStateUpdated))
        assert(events.map(_.transactionId).distinct.size == 1)
        val event = events.last.asInstanceOf[FlowTransactionUpdate]
        event.branchIds should have size 1
      }
    }
  }
}
