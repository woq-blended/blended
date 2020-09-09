package blended.streams.jms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import akka.testkit.TestKit
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Success}

class JmsStreamsupportSpec extends TestKit(ActorSystem("JmsStreamSupport"))
  with LoggingFreeSpecLike
  with Matchers
  with JmsStreamSupport {

  "The JmsStreamSupport process flow should" - {

    "terminate with an exception if the underlying flow fails" in {
      val env : FlowEnvelope = FlowEnvelope(FlowMessage("Hello Blended")(FlowMessage.noProps))

      val flow : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
        Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
          env.withException(new Exception("Boom"))
        }

      processMessages(flow, env) match {
        case Success(s) => fail("Expected a failure")
        case Failure(t) => t.getMessage() should be("Boom")
      }
    }

    "terminate with an exposed kill switch if the underlying flow succeeds" in {

      val env : FlowEnvelope = FlowEnvelope(FlowMessage("Hello Blended")(FlowMessage.noProps))

      val flow : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
        Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env => env }

      processMessages(flow, env) match {
        case Success(s) => s.shutdown()
        case Failure(t) => fail(t)
      }
    }
  }

}
