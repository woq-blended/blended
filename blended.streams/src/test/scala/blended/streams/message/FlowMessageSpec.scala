package blended.streams.message

import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.MsgProperty.Implicits._
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
      val props : FlowMessageProps = Map("foo" -> "bar")
      val msg : FlowMessage = FlowMessage("text", props)

      msg.withHeader("newProp", "test") should be (Success(TextFlowMessage("text", Map("foo" -> "bar", "newProp" -> "test"))))
      msg.withHeader("foo", "newBar") should be (Success(TextFlowMessage("text", Map("foo" -> "newBar"))))
      msg.withHeader("foo", "noBar", overwrite = false) should be (Success(msg))
    }
  }
}
