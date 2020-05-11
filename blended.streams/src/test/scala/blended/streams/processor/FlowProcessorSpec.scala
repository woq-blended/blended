package blended.streams.processor

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import blended.streams.FlowProcessor.IntegrationStep
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger, FlowMessage}
import blended.streams.{FlowHeaderConfig, FlowProcessor}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class FlowProcessorSpec extends TestKit(ActorSystem("FlowProcessorSpec"))
  with LoggingFreeSpecLike
  with Matchers {

  private val log = Logger[FlowProcessorSpec]
  private val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(FlowHeaderConfig.create("App"), log)

  private implicit val materializer = ActorMaterializer()

  private val msg : FlowEnvelope = {
    val header : FlowMessageProps = FlowMessage.props("foo" -> "bar").get
    FlowEnvelope(FlowMessage("Testmessage")(header)).withRequiresAcknowledge(true)
  }

  private val same = new FlowProcessor {
    override val name: String = "identity"
    override val f: IntegrationStep = env => Success(env)
  }

  private val faulty = new FlowProcessor {
    override val name: String = "faulty"
    override val f: IntegrationStep = env => Failure(new Exception("Boom"))
  }

  "The FlowProcessor should" - {

    "process a a simple Intgration step correctly" in {

      val flow = Source.single(msg)
        .via(same.flow(envLogger))
        .runWith(Sink.seq)

      Await.result(flow, 1.second) match {
        case r =>
          r.size should be(1)
          r.head should be(msg.withRequiresAcknowledge(true))
      }
    }

    "process an Exception in an integration step correctly" in {

      val flow = Source.single(msg)
        .via(faulty.flow(envLogger))
        .runWith(Sink.seq)

      Await.result(flow, 1.second) match {
        case r =>
          r.size should be(1)
          assert(r.last.exception.isDefined)
      }

    }
  }
}
