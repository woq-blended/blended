package blended.streams.testsupport

import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

class FlowMessageAssertionSpec extends LoggingFreeSpec
  with Matchers {

  "The FlowMessageAssertions should" - {

    "support to check Expected bodies" in {

      val env = FlowEnvelope(FlowMessage("Hello Blended!")(FlowMessage.noProps))

      val result = FlowMessageAssertion.checkAssertions(env)(
        ExpectedBodies("Hello Blended!")
      )

      result should be (empty)
    }
  }

}
