package blended.streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import blended.streams.FlowProcessor.IntegrationStep
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import MsgProperty.Implicits._

class FlowProcessorSpec extends TestKit(ActorSystem("FlowProcessorSpec"))
  with LoggingFreeSpecLike
  with Matchers {

  private val log = Logger[FlowProcessorSpec]

  private implicit val materializer = ActorMaterializer()

  private val msg : FlowEnvelope = {
    val header : FlowMessageProps = Map("foo" -> "bar")
    FlowEnvelope(FlowMessage("Testmessage", header))
  }

  val same = new FlowProcessor {
    override val name: String = "identity"
    override val f: IntegrationStep = env => Success(Seq(env))
  }

  val multiply = new FlowProcessor {
    override val name: String = "multiply"
    override val f: IntegrationStep = env => Success(1.to(10).map(_ => env))
  }

  val faulty = new FlowProcessor {
    override val name: String = "faulty"
    override val f: IntegrationStep = env => Failure(new Exception("Boom"))
  }

  "The FlowProcessor should" - {

    "process a a simple Intgration step correctly" in {

      val flow = Source.single(msg)
        .via(same.flow(log))
        .runWith(Sink.seq)

      Await.result(flow, 1.second) match {
        case r =>
          r.size should be(1)
          r.head should be(msg.withRequiresAcknowledge(true))
      }
    }

    "process a a multiplying Intgration step correctly" in {

      val flow = Source.single(msg)
        .via(multiply.flow(log))
        .runWith(Sink.seq)

      Await.result(flow, 1.second) match {
        case r =>
          r.size should be (10)
          assert(r.take(9).forall(_ === msg))
          assert(r.last === msg.withRequiresAcknowledge(true))
      }
    }

    "process an Exception in an integration step correctly" in {

      val flow = Source.single(msg)
        .via(faulty.flow(log))
        .runWith(Sink.seq)

      Await.result(flow, 1.second) match {
        case r =>
          r.size should be (1)
          assert(r.last.exception.isDefined)
      }

    }
  }
}
