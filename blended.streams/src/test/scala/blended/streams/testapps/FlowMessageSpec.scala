package blended.streams.testapps

import blended.streams.message.{FlowMessage, TextFlowMessage}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

class FlowMessageSpec extends LoggingFreeSpec
  with Matchers {

  "A Flow Message should" - {

    "Instantiate from a String" in {

      val msg : FlowMessage = FlowMessage("Hallo Andreas", FlowMessage.noProps)

      msg should be (TextFlowMessage("Hallo Andreas", FlowMessage.noProps))
    }
  }
}
