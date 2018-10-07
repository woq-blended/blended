package blended.streams.jms

import akka.util.ByteString
import blended.streams.message.{BinaryFlowMessage, FlowMessage, MsgProperty, TextFlowMessage}
import javax.jms.{BytesMessage, Message, Session, TextMessage}

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

  val flowMessage2jms : (Session, FlowMessage) => Message = { (session, flowMsg) =>

    val msg = flowMsg match {
      case t : TextFlowMessage => session.createTextMessage(t.getText())
      case b : BinaryFlowMessage =>
        val r = session.createBytesMessage()
        r.writeBytes(b.getBytes().toArray)

        r
      case _ => session.createMessage()
    }

    flowMsg.header.filter{
      case (k, v) => !k.startsWith("JMS")
    }.foreach {
      case (k,v) => msg.setObjectProperty(k, v.value)
    }

    msg
  }

}
