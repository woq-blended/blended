package blended.streams.testsupport

import akka.util.ByteString
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

class FlowMessageAssertionSpec extends LoggingFreeSpec
  with Matchers {

  "The FlowMessageAssertions should" - {

    "support to check expected string bodies" in {

      val env = FlowEnvelope(FlowMessage("Hello Blended!")(FlowMessage.noProps))

      FlowMessageAssertion.checkAssertions(env)(
        ExpectedBodies(Some("Hello Blended!"))
      ) should be(empty)

      FlowMessageAssertion.checkAssertions(env)(
        ExpectedBodies(Some("foo"))
      ) should have size 1

      FlowMessageAssertion.checkAssertions(env)(
        ExpectedBodies(Some(100))
      ) should have size 1
    }

    "support to check expected byte string bodies" in {
      val env = FlowEnvelope(FlowMessage(ByteString("Hello Blended!"))(FlowMessage.noProps))

      FlowMessageAssertion.checkAssertions(env)(
        ExpectedBodies(Some(ByteString("Hello Blended!")))
      ) should be(empty)

      FlowMessageAssertion.checkAssertions(env)(
        ExpectedBodies(Some("foo"))
      ) should have size 1
    }
  }

}
