package blended.streams.jms

import akka.util.ByteString
import blended.streams.message.{BinaryFlowMessage, FlowMessage, MsgProperty, TextFlowMessage}
import javax.jms.{BytesMessage, Message, TextMessage}

import scala.collection.JavaConverters._

object JmsFlowMessage {

  val jms2flowMessage : Message => FlowMessage = { msg =>

    val props : Map[String, MsgProperty[_]] = msg.getPropertyNames().asScala.map { name =>
      (name.toString, MsgProperty.lift(msg.getObjectProperty(name.toString())).get)
    }.toMap

    msg match {
      case t : TextMessage =>
        TextFlowMessage(props, t.getText())

      case b : BytesMessage => {
        val content : Array[Byte] = new Array[Byte](b.getBodyLength().toInt)
        b.readBytes(content)
        BinaryFlowMessage(props, ByteString(content))
      }

      case _ => FlowMessage(props)
    }
  }
}
