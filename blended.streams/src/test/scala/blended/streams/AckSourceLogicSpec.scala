package blended.streams

import java.util.concurrent.atomic.AtomicLong

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{ActorMaterializer, Attributes, Materializer, Outlet, SourceShape}
import akka.testkit.TestKit
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.Matchers
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}
import scala.util.Try

object CountingAckSource {
  val counter : AtomicLong = new AtomicLong(0L)
}

class CountingAckSource(
  name : String
)(implicit system : ActorSystem) extends GraphStage[SourceShape[FlowEnvelope]] {

  private val out = Outlet[FlowEnvelope](s"CountingAckSource($name.out)")
  override def shape: SourceShape[FlowEnvelope] = SourceShape(out)

  private class CountingLogic(
    out : Outlet[FlowEnvelope],
    shape : SourceShape[FlowEnvelope],
    numSlots : Int
  ) extends AckSourceLogic[AcknowledgeContext](out, shape) {

    /** The id to identify the instance in the log files */
    override val id: String = s"CountingAckSource-${System.currentTimeMillis()}"

    /** A logger that must be defined by concrete implementations */
    override protected def log: Logger = Logger[CountingAckSource]

    /** The id's of the available inflight slots */
    override protected def inflightSlots(): List[String] = 1.to(numSlots).map(i => s"Count-$i").toList

    override protected def doPerformPoll(id: String): Try[Option[AcknowledgeContext]] = Try {

      val msg : FlowMessage = FlowMessage(FlowMessage.props("Counter" -> CountingAckSource.counter.incrementAndGet()).get)

      Some(AcknowledgeContext(
        inflightId = id,
        envelope = FlowEnvelope(msg),
        state = AckState.Pending
      ))

    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new CountingLogic(out, shape, 5)
}

class AckSourceLogicSpec extends TestKit(ActorSystem("AckSourceLogic"))
  with LoggingFreeSpecLike
  with Matchers {

  "The AckSourceLogic should" - {

    "Generate messages correctly" in {

      implicit val materializer : Materializer = ActorMaterializer()

      val s : Source[FlowEnvelope, NotUsed] = Source.fromGraph(new CountingAckSource("TestCounter"))

      val fCounting : Future[Seq[FlowEnvelope]] = s.take(100).toMat(Sink.seq[FlowEnvelope])(Keep.right).run()

      val counting : Seq[FlowEnvelope] = Await.result(fCounting, 3.seconds)
      counting should have size(100)
    }
  }

}
