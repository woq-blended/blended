package blended.streams.dispatcher.internal.builder

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream.scaladsl.{GraphDSL, Keep, Source}
import akka.stream.{ActorMaterializer, KillSwitches, OverflowStrategy, SinkShape}
import blended.jms.utils.JmsDestination
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope}
import blended.streams.testsupport.Collector
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionFailed, FlowTransactionUpdate}
import blended.streams.worklist.{WorklistEvent, WorklistStarted, WorklistState, WorklistStepCompleted}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class WorklistEventhandlerSpec extends LoggingFreeSpec
  with Matchers
  with DispatcherSpecSupport {

  private def runEventHandler(
    ctxt : DispatcherExecContext
  ) = {
    implicit val system : ActorSystem = ctxt.system
    implicit val materializer = ActorMaterializer()

    val errColl = Collector[FlowEnvelope]("error")
    val transColl = Collector[FlowTransactionEvent]("transaction")

    val source = Source.actorRef[WorklistEvent](10, OverflowStrategy.fail)

    val sinkGraph = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val evtHandler = b.add(DispatcherBuilder(ctxt.idSvc, ctxt.cfg)(ctxt.bs).worklistEventHandler())
      val err = b.add(errColl.sink)
      val trans = b.add(transColl.sink)

      evtHandler.out0 ~> trans.in
      evtHandler.out1 ~> err.in

      SinkShape(evtHandler.in)
    }

    val (actor, killSwitch) = source
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(sinkGraph)(Keep.left).run()

    (actor, killSwitch, transColl, errColl)
  }

  private def run(vendor: String, provider: String, autoComplete : Boolean*)(f : (FlowEnvelope, List[FlowTransactionEvent]) => Unit) =
    withDispatcherConfig { ctxt =>
      implicit val eCtxt = ctxt.system.dispatcher

      val (actor, killSwitch, transColl, errColl) = runEventHandler(ctxt)

      val master = FlowEnvelope()

      val steps = autoComplete.zipWithIndex.map { case (compl, idx) =>
        master
          .withHeader(ctxt.bs.headerBranchId, "step-" + idx).get
          .withHeader(ctxt.bs.headerAutoComplete, compl).get
          .withHeader(ctxt.bs.headerBridgeVendor, vendor).get
          .withHeader(ctxt.bs.headerBridgeProvider,provider).get
          .withHeader(ctxt.bs.headerBridgeDest, JmsDestination.create("test").get.asString).get
      }

      val wl = ctxt.bs.worklist(steps:_*).get

      val started = WorklistStarted(worklist = wl, 10.seconds)

      // Start a dummy worklist
      actor ! started
      actor ! WorklistStepCompleted(wl, WorklistState.Completed)

      akka.pattern.after(1.second, ctxt.system.scheduler)( Future { killSwitch.shutdown() } )

      val transEvents = transColl.probe.expectMsgType[List[FlowTransactionEvent]]
      f(master, transEvents)
    }

  "The worklist event handler should" - {

    "Generate a Transaction update when a new worklist is started" in {

      withDispatcherConfig { ctxt =>
        implicit val eCtxt = ctxt.system.dispatcher

        val (actor, killSwitch, transColl, errColl) = runEventHandler(ctxt)

        val envelope = FlowEnvelope().withHeader(ctxt.bs.headerBranchId, "test").get
        val wl = ctxt.bs.worklist(envelope).get

        val started = WorklistStarted(worklist = wl, timeout = 10.seconds)
        val done = WorklistStepCompleted(worklist = wl, state = WorklistState.Completed)

        // Start a dummy worklist
        actor ! started

        akka.pattern.after(1.second, ctxt.system.scheduler)( Future { killSwitch.shutdown() } )

        val transEvents = transColl.probe.expectMsgType[List[FlowTransactionEvent]]
        transEvents.size should be (1)

        val event = transEvents.head.asInstanceOf[FlowTransactionUpdate]
        event.branchIds should be (Seq("test"))

        event.updatedState should be (WorklistState.Started)
        event.transactionId should be (envelope.id)
      }
    }

    "Generate a transaction failed event when the worklist has failed" in {
      withDispatcherConfig { ctxt =>
        implicit val eCtxt = ctxt.system.dispatcher

        val (actor, killSwitch, transColl, errColl) = runEventHandler(ctxt)

        val envelope = FlowEnvelope().withHeader(ctxt.bs.headerBranchId, "test").get
        val wl = ctxt.bs.worklist(envelope).get

        val started = WorklistStarted(worklist = wl, timeout = 10.seconds)
        val done = WorklistStepCompleted(worklist = wl, state = WorklistState.Completed)

        // Start a dummy worklist
        actor ! started
        actor ! WorklistStepCompleted(worklist = wl, state = WorklistState.Failed)

        akka.pattern.after(1.second, ctxt.system.scheduler)( Future { killSwitch.shutdown() } )

        val transEvents = transColl.probe.expectMsgType[List[FlowTransactionEvent]]
        transEvents.size should be (2)

        val event = transEvents.last.asInstanceOf[FlowTransactionFailed]
        event.transactionId should be (envelope.id)
      }
    }

    "Generate a transaction failed event when the worklist times out" in {
      withDispatcherConfig { ctxt =>
        implicit val eCtxt = ctxt.system.dispatcher

        val (actor, killSwitch, transColl, errColl) = runEventHandler(ctxt)

        val envelope = FlowEnvelope().withHeader(ctxt.bs.headerBranchId, "test").get
        val wl = ctxt.bs.worklist(envelope).get

        val started = WorklistStarted(worklist = wl)

        // Start a dummy worklist
        actor ! started

        akka.pattern.after(1.second, ctxt.system.scheduler)( Future { killSwitch.shutdown() } )

        val transEvents = transColl.probe.expectMsgType[List[FlowTransactionEvent]]
        transEvents.size should be (2)

        val event = transEvents.last.asInstanceOf[FlowTransactionFailed]
        event.transactionId should be (envelope.id)
      }
    }

    "Acknowledge the inbound envelope if the worklist has been completed in" in {

      val ackCount : AtomicInteger = new AtomicInteger(0)

      withDispatcherConfig { ctxt =>
        implicit val eCtxt = ctxt.system.dispatcher

        val (actor, killSwitch, transColl, errColl) = runEventHandler(ctxt)

        val envelope = FlowEnvelope()
          .withHeader(ctxt.bs.headerBranchId, "test").get
          .withRequiresAcknowledge(true)
          .withAckHandler(Some(new AcknowledgeHandler {
            override def acknowledge: FlowEnvelope => Try[Unit] = _ => Try(ackCount.incrementAndGet())
          }))

        val wl = ctxt.bs.worklist(envelope).get

        val started = WorklistStarted(worklist = wl, 10.seconds)

        // Start a dummy worklist
        actor ! started
        actor ! WorklistStepCompleted(wl, WorklistState.Completed)

        akka.pattern.after(1.second, ctxt.system.scheduler)( Future { killSwitch.shutdown() } )

        val transEvents = transColl.probe.expectMsgType[List[FlowTransactionEvent]]
        transEvents.size should be (1)

        ackCount.get() should be (1)
      }
    }

    "Generate a transaction updated after the worklist has completed for all outbounds which are routed internally and should autocomplete"  in {

      run("activemq", "activemq", true){ (envelope, events) =>
        events.size should be (2)

        val event = events.last.asInstanceOf[FlowTransactionUpdate]
        event.branchIds should be (Seq("step-0"))
        event.updatedState should be (WorklistState.Completed)
      }

      run("activemq", "activemq", true, true, true){ (envelope, events) =>
        events.size should be (2)

        val event = events.last.asInstanceOf[FlowTransactionUpdate]
        event.branchIds should have size(3)
        event.updatedState should be (WorklistState.Completed)
      }

      run("activemq", "activemq", false){ (envelope, events) =>
        events.size should be (1)

        val event = events.last.asInstanceOf[FlowTransactionUpdate]
        event.transactionId should be (envelope.id)
      }

      run("activemq", "activemq", false, true){ (envelope, events) =>
        events.size should be (2)

        val event = events.last.asInstanceOf[FlowTransactionUpdate]
        event.branchIds should be (Seq("step-1"))
        event.transactionId should be (envelope.id)
        event.updatedState should be (WorklistState.Completed)
      }
    }
  }
}
