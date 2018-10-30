package blended.streams.dispatcher.internal.builder

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream.scaladsl.{GraphDSL, Keep, Source}
import akka.stream.{ActorMaterializer, KillSwitches, OverflowStrategy, SinkShape}
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope}
import blended.streams.testsupport.Collector
import blended.streams.transaction.{FlowTransactionCompleted, FlowTransactionEvent, FlowTransactionFailed, FlowTransactionUpdate}
import blended.streams.worklist.{WorklistEvent, WorklistStarted, WorklistState, WorklistStepCompleted}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class WorklistEventhandlerSpec extends LoggingFreeSpec
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

      val evtHandler = b.add(DispatcherBuilder(ctxt.idSvc, ctxt.cfg).worklistEventHandler())
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

  "The worklist event handler should" - {

    "Generate a Transaction update when a new worklist is started" in {

      withDispatcherConfig { ctxt =>
        implicit val eCtxt = ctxt.system.dispatcher

        val (actor, killSwitch, transColl, errColl) = runEventHandler(ctxt)

        val envelope = FlowEnvelope().withHeader(bs.headerOutboundId, "test").get
        val wl = bs.worklist(envelope).get

        val started = WorklistStarted(worklist = wl, timeout = 10.seconds)
        val done = WorklistStepCompleted(worklist = wl, state = WorklistState.Completed)

        // Start a dummy worklist
        actor ! started

        akka.pattern.after(1.second, ctxt.system.scheduler)( Future { killSwitch.shutdown() } )

        val transEvents = transColl.probe.expectMsgType[List[FlowTransactionEvent]]
        transEvents.size should be (1)

        val event = transEvents.head.asInstanceOf[FlowTransactionUpdate]
        event.envelopes should have size(1)
        event.envelopes.head should be (envelope)

        event.updatedState should be (WorklistState.Started)
        event.transactionId should be (envelope.id)
      }
    }

    "Generate a transaction failed event when the worklist has failed" in {
      withDispatcherConfig { ctxt =>
        implicit val eCtxt = ctxt.system.dispatcher

        val (actor, killSwitch, transColl, errColl) = runEventHandler(ctxt)

        val envelope = FlowEnvelope().withHeader(bs.headerOutboundId, "test").get
        val wl = bs.worklist(envelope).get

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

        val envelope = FlowEnvelope().withHeader(bs.headerOutboundId, "test").get
        val wl = bs.worklist(envelope).get

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

    "Acknowledge the inbound envelope if the worklis has been completed in" in {

      val ackCount : AtomicInteger = new AtomicInteger(0)

      withDispatcherConfig { ctxt =>
        implicit val eCtxt = ctxt.system.dispatcher

        val (actor, killSwitch, transColl, errColl) = runEventHandler(ctxt)

        val envelope = FlowEnvelope()
          .withHeader(bs.headerOutboundId, "test").get
          .withRequiresAcknowledge(true)
          .withAckHandler(Some(new AcknowledgeHandler {
            override def acknowledge: FlowEnvelope => Try[Unit] = _ => Try(ackCount.incrementAndGet())
          }))

        val wl = bs.worklist(envelope).get

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

    "Generate a transaction completed if the worklist is completed and ALL outbounds are configured with autoComplete" in {

      def run(autoComplete : Boolean*)(f : (FlowEnvelope, List[FlowTransactionEvent]) => Unit) =
        withDispatcherConfig { ctxt =>
          implicit val eCtxt = ctxt.system.dispatcher

          val (actor, killSwitch, transColl, errColl) = runEventHandler(ctxt)

          val master = FlowEnvelope()

          val steps = autoComplete.zipWithIndex.map { case (compl, idx) =>
            master
              .withHeader(bs.headerOutboundId, "step-" + idx).get
              .withHeader(bs.headerAutoComplete, compl).get
          }

          val wl = bs.worklist(steps:_*).get

          val started = WorklistStarted(worklist = wl, 10.seconds)

          // Start a dummy worklist
          actor ! started
          actor ! WorklistStepCompleted(wl, WorklistState.Completed)

          akka.pattern.after(1.second, ctxt.system.scheduler)( Future { killSwitch.shutdown() } )

          val transEvents = transColl.probe.expectMsgType[List[FlowTransactionEvent]]
          f(master, transEvents)
        }

      run(true){ (envelope, events) =>
        events.size should be (2)

        val event = events.last.asInstanceOf[FlowTransactionCompleted]
        event.transactionId should be (envelope.id)
      }

      run(true, true, true){ (envelope, events) =>
        events.size should be (2)

        val event = events.last.asInstanceOf[FlowTransactionCompleted]
        event.transactionId should be (envelope.id)
      }

      run(false){ (envelope, events) =>
        events.size should be (1)

        val event = events.last.asInstanceOf[FlowTransactionUpdate]
        event.transactionId should be (envelope.id)
      }

      run(false, true){ (envelope, events) =>
        events.size should be (1)

        val event = events.last.asInstanceOf[FlowTransactionUpdate]
        event.transactionId should be (envelope.id)
      }
    }
  }
}
