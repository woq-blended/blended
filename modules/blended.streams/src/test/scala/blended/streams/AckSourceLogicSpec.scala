package blended.streams

import java.util.concurrent.atomic.AtomicLong

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Graph, Outlet, SourceShape}
import akka.testkit.TestKit
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope, FlowEnvelopeLogger, FlowMessage}
import blended.streams.processor.{AckProcessor, Collector}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Try

class CountingAckContext(
  id : String,
  env : FlowEnvelope
)(handleAck : CountingAckContext => Unit)(handleDeny : CountingAckContext => Unit)
  extends DefaultAcknowledgeContext(id, env, System.currentTimeMillis()) {
  override def acknowledge(): Unit = handleAck(this)
  override def deny(): Unit = handleDeny(this)
}

class CountingAckSource(
  name : String,
  msgCount : Long,
  numSlots : Int,
  autoAcknowledge : Boolean
)(
  handleAck : AcknowledgeContext => Unit
)(
  handleDeny : AcknowledgeContext => Unit
) extends GraphStage[SourceShape[FlowEnvelope]] {

  private val out : Outlet[FlowEnvelope] = Outlet[FlowEnvelope](s"CountingAckSource($name.out)")
  override def shape : SourceShape[FlowEnvelope] = SourceShape(out)

  private class CountingLogic(
    shape : SourceShape[FlowEnvelope],
    out : Outlet[FlowEnvelope],
    msgCount : Long,
    numSlots : Int,
    autoAck : Boolean
  ) extends AckSourceLogic[CountingAckContext](shape, out) {

    private val counter : AtomicLong = new AtomicLong(0L)

    override protected val autoAcknowledge: Boolean = autoAck

    /** The id to identify the instance in the log files */
    override protected val id : String = s"CountingAckSource-${System.currentTimeMillis()}"

    /** A logger that must be defined by concrete implementations */
    override protected def log: FlowEnvelopeLogger = FlowEnvelopeLogger.create(FlowHeaderConfig.create("App"), Logger[CountingAckSource])

    /** The id's of the available inflight slots */
    override protected val inflightSlots : List[String] = 1.to(numSlots).map(i => s"Count-$i").toList

    override protected def doPerformPoll(id : String, ackHandler : AcknowledgeHandler) : Try[Option[CountingAckContext]] = Try {

      if (counter.incrementAndGet() <= msgCount) {
        val msg : FlowMessage = FlowMessage(FlowMessage.props("Counter" -> counter.get()).get)

        Some(new CountingAckContext(
          id = id,
          env = FlowEnvelope(msg)
            .withRequiresAcknowledge(true)
            .withAckHandler(Some(ackHandler))
        )(handleAck)(handleDeny))
      } else {
        None
      }
    }
  }

  override def createLogic(inheritedAttributes : Attributes) : GraphStageLogic = new CountingLogic(shape, out, msgCount, numSlots, autoAcknowledge)
}

class AckSourceLogicSpec extends TestKit(ActorSystem("AckSourceLogic"))
  with LoggingFreeSpecLike
  with Matchers {

  private val numSlots : Int = 5
  private val expectedCnt : Long = numSlots * 2
  private val log : Logger = Logger[AckSourceLogicSpec]
  private val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(FlowHeaderConfig.create("App"), log)

  "The AckSourceLogic should" - {

    "Generate messages and process the acknowledgement correctly" in {

      implicit val timeout : FiniteDuration = 1.seconds

      val ack : AtomicLong = new AtomicLong(0L)
      val deny : AtomicLong = new AtomicLong(0L)

      val ackSource : Graph[SourceShape[FlowEnvelope], NotUsed] =
        new CountingAckSource("AckCounter", expectedCnt, numSlots, autoAcknowledge = false)(_ => ack.incrementAndGet())(_ => deny.incrementAndGet())

      val s : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(ackSource)
          .via(new AckProcessor("AckCounter-ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("AckCounter", s, Some(timeout))

      Await.result(collector.result, timeout + 100.millis) should have size expectedCnt

      // The last batch of numSlots will not necessarily be acknowledged yet
      ack.get() should be(expectedCnt)
      deny.get() should be(0L)
    }

    "Generate messages and process the auto acknowledgement correctly" in {

      implicit val timeout : FiniteDuration = 1.seconds

      val ack : AtomicLong = new AtomicLong(0L)
      val deny : AtomicLong = new AtomicLong(0L)

      val ackSource : Graph[SourceShape[FlowEnvelope], NotUsed] =
        new CountingAckSource("AckCounter", expectedCnt, numSlots, autoAcknowledge = true)(_ => ack.incrementAndGet())(_ => deny.incrementAndGet())

      val s : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(ackSource)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("AckCounter", s, Some(timeout))

      Await.result(collector.result, timeout + 100.millis) should have size expectedCnt

      // The last batch of numSlots will not necessarily be acknowledged yet
      ack.get() should be(expectedCnt)
      deny.get() should be(0L)
    }

    "Generate messages and process the denial correctly" in {

      implicit val timeout : FiniteDuration = 1.seconds

      val ack : AtomicLong = new AtomicLong(0L)
      val deny : AtomicLong = new AtomicLong(0L)

      val ackSource : Graph[SourceShape[FlowEnvelope], NotUsed] =
        new CountingAckSource("AckCounter", expectedCnt, numSlots, autoAcknowledge = false)(_ => ack.incrementAndGet())(_ => deny.incrementAndGet())

      val s : Source[FlowEnvelope, NotUsed] =
        Source.fromGraph(ackSource)
          .via(FlowProcessor.fromFunction("deny", envLogger){ env => Try { throw new Exception("Boom")}})
          .via(new AckProcessor("DenyCounter-ack").flow)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("AckCounter", s, Some(timeout))

      Await.result(collector.result, timeout + 100.millis) should have size expectedCnt

      // The last batch of numSlots will not necessarily be acknowledged yet
      ack.get() should be(0L)
      deny.get() should be(expectedCnt)
    }

    "Monitor the acknowledgement for timeouts correctly" in {

      val timeout : FiniteDuration = 3.seconds

      val ack : AtomicLong = new AtomicLong(0L)
      val deny : AtomicLong = new AtomicLong(0L)

      val ackSource : Graph[SourceShape[FlowEnvelope], NotUsed] =
        new CountingAckSource("AckCounter", 1, numSlots, autoAcknowledge = false)(_ => ack.incrementAndGet())(_ => deny.incrementAndGet())

      val s : Source[FlowEnvelope, NotUsed] = Source.fromGraph(ackSource)

      val collector : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit("AckCounter", s, Some(timeout - 1.second))

      Await.result(collector.result, timeout) should have size 1

      // The last batch of numSlots will not necessarily be acknowledged yet
      ack.get() should be(0L)
      deny.get() should be(1)
    }
  }

}
