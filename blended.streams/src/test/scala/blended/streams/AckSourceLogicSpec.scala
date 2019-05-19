package blended.streams

import java.util.concurrent.atomic.AtomicLong

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{ActorMaterializer, Attributes, Graph, KillSwitches, Materializer, Outlet, SourceShape}
import akka.testkit.TestKit
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope, FlowMessage}
import blended.streams.processor.{AckProcessor, Collector}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

class CountingAckSource(
  name : String,
  msgCount : Long,
  numSlots : Int
)(handleAck : AcknowledgeContext => Unit)(handleDeny : AcknowledgeContext => Unit)(implicit system : ActorSystem) extends GraphStage[SourceShape[FlowEnvelope]] {

  private val out = Outlet[FlowEnvelope](s"CountingAckSource($name.out)")
  override def shape: SourceShape[FlowEnvelope] = SourceShape(out)

  private class CountingLogic(
    out : Outlet[FlowEnvelope],
    shape : SourceShape[FlowEnvelope],
    msgCount : Long,
    numSlots : Int
  ) extends AckSourceLogic[AcknowledgeContext](out, shape) {

    private val counter : AtomicLong = new AtomicLong(0L)

    /** The id to identify the instance in the log files */
    override val id: String = s"CountingAckSource-${System.currentTimeMillis()}"

    /** A logger that must be defined by concrete implementations */
    override protected def log: Logger = Logger[CountingAckSource]

    /** The id's of the available inflight slots */
    override protected def inflightSlots(): List[String] = 1.to(numSlots).map(i => s"Count-$i").toList

    override protected def beforeAcknowledge(ackCtxt: AcknowledgeContext): Unit = handleAck(ackCtxt)

    override protected def beforeDenied(ackCtxt: AcknowledgeContext): Unit = handleDeny(ackCtxt)

    override protected def doPerformPoll(id: String, ackHandler : AcknowledgeHandler): Try[Option[AcknowledgeContext]] = Try {

      if (counter.incrementAndGet() <= msgCount) {
        val msg : FlowMessage = FlowMessage(FlowMessage.props("Counter" -> counter.get()).get)

        Some(new DefaultAcknowledgeContext(
          inflightId = id,
          envelope = FlowEnvelope(msg)
            .withRequiresAcknowledge(true)
            .withAckHandler(Some(ackHandler))
        ))
      } else {
        None
      }
   }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new CountingLogic(out, shape, msgCount, numSlots)
}

class AckSourceLogicSpec extends TestKit(ActorSystem("AckSourceLogic"))
  with LoggingFreeSpecLike
  with Matchers {

  private val numSlots : Int = 10
  private val expectedCnt : Long = numSlots * 2
  private val log : Logger = Logger[AckSourceLogicSpec]
  private implicit val eCtxt : ExecutionContext = system.dispatcher

  "The AckSourceLogic should" - {

    "Generate messages and process the acknowledgement correctly" in {

      implicit val materializer : Materializer = ActorMaterializer()
      implicit val timeout : FiniteDuration = 1.seconds

      val ack : AtomicLong = new AtomicLong(0L)
      val deny : AtomicLong = new AtomicLong(0L)

      val ackSource : Graph[SourceShape[FlowEnvelope], NotUsed] =
        new CountingAckSource("AckCounter", expectedCnt, numSlots)(_ => ack.incrementAndGet())(_ => deny.incrementAndGet())

      val s : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(ackSource)
          .via(new AckProcessor("AckCounter-ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("AckCounter", s, timeout){ env => }

      Await.result(collector.result, timeout + 100.millis) should have size(expectedCnt)

      // The last batch of numSlots will not necessarily be acknowldged yet
      ack.get() should be (expectedCnt)
      deny.get() should be(0L)
    }

    "Generate messages and process the denial correctly" in {

      implicit val materializer : Materializer = ActorMaterializer()
      implicit val timeout : FiniteDuration = 1.seconds

      val ack : AtomicLong = new AtomicLong(0L)
      val deny : AtomicLong = new AtomicLong(0L)

      val ackSource : Graph[SourceShape[FlowEnvelope], NotUsed] =
        new CountingAckSource("AckCounter", expectedCnt, numSlots)(_ => ack.incrementAndGet())(_ => deny.incrementAndGet())

      val s : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(ackSource)
          .via(FlowProcessor.fromFunction("deny", log){ env => Try { throw new Exception("Boom")}})
          .via(new AckProcessor("DenyCounter-ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("AckCounter", s, timeout){ env => }

      Await.result(collector.result, timeout + 100.millis) should have size(expectedCnt)

      // The last batch of numSlots will not necessarily be acknowldged yet
      ack.get() should be (0L)
      deny.get() should be(expectedCnt)
    }

    "Monitor the acknowledgement for timeouts correctly" in {

      implicit val materializer : Materializer = ActorMaterializer()
      val timeout : FiniteDuration = 3.seconds

      val ack : AtomicLong = new AtomicLong(0L)
      val deny : AtomicLong = new AtomicLong(0L)

      val ackSource : Graph[SourceShape[FlowEnvelope], NotUsed] =
        new CountingAckSource("AckCounter", 1, numSlots)(_ => ack.incrementAndGet())(_ => deny.incrementAndGet())

      val s : Source[FlowEnvelope, NotUsed] = Source.fromGraph(ackSource)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("AckCounter", s, timeout){ env => }

      Await.result(collector.result, timeout + 100.millis) should have size(1)

      // The last batch of numSlots will not necessarily be acknowldged yet
      ack.get() should be (0L)
      deny.get() should be(1)
    }
  }

}
