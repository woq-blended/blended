package blended.streams.dispatcher.internal.builder

import java.io.File

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Source}
import blended.streams.message.FlowEnvelope
import blended.streams.testsupport.Collector
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionState, FlowTransactionUpdate}
import blended.streams.worklist.WorklistState
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.Future
import scala.concurrent.duration._

class DispatcherSpec extends LoggingFreeSpec
  with Matchers
  with DispatcherSpecSupport {

  override def country: String = "cc"
  override def location: String = "09999"
  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()
  override def loggerName: String = getClass().getName()

  implicit val bs : DispatcherBuilderSupport = new DispatcherBuilderSupport {
    override val prefix: String = "App"
    override val streamLogger: Logger = Logger(loggerName)
  }

  private def runDispatcher(
    ctxt : DispatcherExecContext,
    send: Flow[FlowEnvelope, FlowEnvelope, NotUsed]
  ) : (ActorRef, KillSwitch, Collector[FlowTransactionEvent]) = {

    implicit val system : ActorSystem = ctxt.system
    implicit val materializer : Materializer = ActorMaterializer()

    val transColl = Collector[FlowTransactionEvent]("trans")

    val source = Source.actorRef[FlowEnvelope](10, OverflowStrategy.fail)

    val sinkGraph = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val dispatcher = b.add(DispatcherBuilder(ctxt.idSvc, ctxt.cfg).dispatcher(send))
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

  val goodSend = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
    bs.streamLogger.info(s"Outbound send : $env")
    env
  }

  private def runTest[T](testMsg: FlowEnvelope*)(f : List[FlowTransactionEvent] => T) : T = {
    withDispatcherConfig { ctxt =>
      implicit val eCtxt = ctxt.system.dispatcher

      val (actor, killswitch, coll) = runDispatcher(ctxt, goodSend)

      akka.pattern.after(500.millis, ctxt.system.scheduler)( Future { killswitch.shutdown() } )

      testMsg.foreach(env => actor ! env)

      f(coll.probe.expectMsgType[List[FlowTransactionEvent]])
    }
  }


  "The Dispatcher should" - {

    "produce a transaction failed event in case the flow fails with an exception" in {

      val testMsgs = Seq(
        FlowEnvelope(),
        FlowEnvelope().withHeader(bs.headerResourceType, "Dummy").get,
        FlowEnvelope().withHeader(bs.headerResourceType, "NoOutbound").get,
      )

      runTest(testMsgs:_*){ events =>
        events should have size (testMsgs.size)
        assert(events.forall(_.state == FlowTransactionState.Failed))
      }
    }

    "produce a transaction update event for the started worklist if the envelope is only routed externally" in {

      val testMsgs = Seq(
        FlowEnvelope().withHeader(bs.headerResourceType, "NoCbe").get,
      )

      runTest(testMsgs:_*){ events =>
        events should have size(testMsgs.size)
        events.foreach { e =>
          val event = e.asInstanceOf[FlowTransactionUpdate]
          event.state should be (FlowTransactionState.Updated)
          event.updatedState should be (WorklistState.Started)
        }
      }
    }

    "produce a transaction event for the started worklist and one transaction update for each outbound flow that is routed internal" in {
      val testMsgs = Seq(
        FlowEnvelope().withHeader(bs.headerResourceType, "FanOut").get,
      )

      runTest(testMsgs:_*){ events =>
        events should have size(2)

        val event = events.last.asInstanceOf[FlowTransactionUpdate]
        event.state should be (FlowTransactionState.Updated)
        event.branchIds should have size(1)
      }
    }
  }
}
