package blended.streams.testapps

import blended.streams.message.{BaseFlowMessage, BinaryFlowMessage, FlowMessage, TextFlowMessage}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

import scala.util.Success

class FlowMessageSpec extends LoggingFreeSpec
  with Matchers {

  "A Flow Message should" - {

    "Instantiate from a String" in {
      val msg : FlowMessage = FlowMessage("Hallo Andreas", FlowMessage.noProps)
      msg should be (TextFlowMessage("Hallo Andreas", FlowMessage.noProps))
    }

    "Instantiate with properties only" in {
      val msg : FlowMessage = FlowMessage(Map("foo" -> "bar"))
      msg should be (BaseFlowMessage(Map("foo" -> "bar")))
    }

    "Instantiate from a byte Array" in {

      val b = "Hallo Blended!".getBytes()
      val msg : FlowMessage = FlowMessage(b, FlowMessage.noProps)
      msg should be (BinaryFlowMessage(b, FlowMessage.noProps))
    }

    "Allow to set and overwrite a property" in {
      val msg : FlowMessage = FlowMessage(Map("foo" -> "bar"))

      msg.withHeader("newProp", "test") should be (Success(BaseFlowMessage(Map("foo" -> "bar", "newProp" -> "test"))))
      msg.withHeader("foo", "newBar") should be (Success(BaseFlowMessage(Map("foo" -> "newBar"))))
      msg.withHeader("foo", "noBar", overwrite = false) should be (Success(msg))
    }
  }
}
