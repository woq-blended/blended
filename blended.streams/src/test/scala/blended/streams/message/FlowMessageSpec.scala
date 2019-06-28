package blended.streams.message

import blended.streams.message.FlowMessage.FlowMessageProps
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import blended.util.RichTry._

import scala.util.Success

class FlowMessageSpec extends LoggingFreeSpec
  with Matchers
  with PropertyChecks {

  "A Flow Message should" - {

    "Instantiate from a String" in {
      val msg : FlowMessage = FlowMessage("Hallo Andreas")(FlowMessage.noProps)
      msg should be(TextFlowMessage("Hallo Andreas", FlowMessage.noProps))
    }

    "Instantiate with properties only" in {
      val msg : FlowMessage = FlowMessage(FlowMessage.props("foo" -> "bar").unwrap)
      msg should be(BaseFlowMessage(FlowMessage.props("foo" -> "bar").unwrap))
    }

    "Instantiate from a byte Array" in {

      val b = "Hallo Blended!".getBytes()
      val msg : FlowMessage = FlowMessage(b)(FlowMessage.noProps)
      msg should be(BinaryFlowMessage(b, FlowMessage.noProps))
    }

    "Allow to set and overwrite a property" in {
      val props : FlowMessageProps = FlowMessage.props("foo" -> "bar").unwrap
      val msg : FlowMessage = FlowMessage("text")(props)

      msg.withHeader("newProp", "test") should be(
        Success(TextFlowMessage("text", FlowMessage.props("foo" -> "bar", "newProp" -> "test").unwrap))
      )

      msg.withHeader("foo", "newBar") should be(
        Success(TextFlowMessage("text", FlowMessage.props("foo" -> "newBar").unwrap))
      )

      msg.withHeader("foo", "noBar", overwrite = false) should be(Success(msg))
    }

    "Support all property types correctly" in {

      val unitName : String = "unit"
      //scalastyle:off null
      val unitProps : FlowMessageProps = FlowMessage.props(unitName -> null).unwrap
      //scalastyle:on null
      val unitMsg : FlowMessage = FlowMessage(unitProps)
      unitMsg.header[Unit](unitName) should be(Some(()))

      forAll { (n : Int, propName : String) =>
        whenever(propName.nonEmpty) {
          val props : FlowMessageProps = FlowMessage.props(propName -> n).unwrap
          val msg = FlowMessage(props)
          msg.header[Int](propName) should be(Some(n))
          msg.header[Integer](propName) should be(Some(n))
        }
      }

      forAll { (n : Long, propName : String) =>
        whenever(propName.nonEmpty) {
          val props : FlowMessageProps = FlowMessage.props(propName -> n).unwrap
          val msg = FlowMessage(props)
          msg.header[Long](propName) should be(Some(n))
          msg.header[java.lang.Long](propName) should be(Some(n))
        }
      }

      forAll { (n : Short, propName : String) =>
        whenever(propName.nonEmpty) {
          val props : FlowMessageProps = FlowMessage.props(propName -> n).unwrap
          val msg = FlowMessage(props)
          msg.header[Short](propName) should be(Some(n))
          msg.header[java.lang.Short](propName) should be(Some(n))
        }
      }

      forAll { (n : Float, propName : String) =>
        whenever(propName.nonEmpty) {
          val props : FlowMessageProps = FlowMessage.props(propName -> n).unwrap
          val msg = FlowMessage(props)
          msg.header[Float](propName) should be(Some(n))
          msg.header[java.lang.Float](propName) should be(Some(n))
        }
      }

      forAll { (n : Double, propName : String) =>
        whenever(propName.nonEmpty) {
          val props : FlowMessageProps = FlowMessage.props(propName -> n).unwrap
          val msg = FlowMessage(props)
          msg.header[Double](propName) should be(Some(n))
          msg.header[java.lang.Double](propName) should be(Some(n))
        }
      }

      forAll { (n : Boolean, propName : String) =>
        whenever(propName.nonEmpty) {
          val props : FlowMessageProps = FlowMessage.props(propName -> n).unwrap
          val msg : FlowMessage = FlowMessage(props)
          msg.header[Boolean](propName) should be(Some(n))
          msg.header[java.lang.Boolean](propName) should be(Some(n))
        }
      }

      forAll { (n : Byte, propName : String) =>
        whenever(propName.nonEmpty) {
          val props : FlowMessageProps = FlowMessage.props(propName -> n).unwrap
          val msg : FlowMessage = FlowMessage(props)
          msg.header[Byte](propName) should be(Some(n))
          msg.header[java.lang.Byte](propName) should be(Some(n))
        }
      }

      forAll { (n : String, propName : String) =>
        whenever(propName.nonEmpty) {
          val props : FlowMessageProps = FlowMessage.props(propName -> n).unwrap
          val msg : FlowMessage = FlowMessage(props)
          msg.header[String](propName) should be(Some(n))
          msg.header[java.lang.String](propName) should be(Some(n))
        }
      }
    }
  }
}
