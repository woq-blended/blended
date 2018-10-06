package blended.streams.jms

import java.util.concurrent.Semaphore

import akka.stream.stage._
import akka.stream.{Attributes, KillSwitch, Outlet, SourceShape}
import akka.util.ByteString
import blended.jms.utils.{JmsConsumerSession, JmsDestination}
import blended.streams.message.{BinaryFlowMessage, FlowMessage, MsgProperty, TextFlowMessage}
import javax.jms._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

object JmsSourceStage {

  val jms2flowMessage : Message => FlowMessage = { msg =>

    val props : Map[String, MsgProperty[_]] = msg.getPropertyNames().asScala.map { name =>
      (name.toString, MsgProperty.lift(msg.getObjectProperty(name.toString())).get)
    }.toMap

    msg match {
      case t : TextMessage => TextFlowMessage(props, t.getText())
      case b : BytesMessage => {
        val content : Array[Byte] = new Array[Byte](b.getBodyLength().toInt)
        b.readBytes(content)
        BinaryFlowMessage(props, ByteString(content))
      }
      case _ => FlowMessage(props)
    }
  }
}

class JmsSourceStage(settings: JMSConsumerSettings) extends GraphStageWithMaterializedValue[SourceShape[FlowMessage], KillSwitch] {

  private val out = Outlet[FlowMessage]("JmsSource.out")

  override def shape: SourceShape[FlowMessage] = SourceShape(out)

//  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
//
//    val logic = new GraphStageLogic(shape) with StageLogging {
//
//      override def preStart(): Unit = {
//        log.info("Starting JMS Source stage")
//      }
//
//      setHandler(
//        out, new OutHandler {
//          override def onPull(): Unit = {
//            //log.info("Pushing new message")
//            push(out, FlowMessage(Map.empty[String, MsgProperty[_]], "Hallo Andreas"))
//          }
//        }
//      )
//
//      override def postStop(): Unit = {
//        log.debug("Stopping JMS Source stage")
//      }
//    }
//
//    logic
//  }

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, KillSwitch) = {

    val logic = new SourceStageLogic(shape, out, settings, inheritedAttributes) {

      private val bufferSize = (settings.bufferSize + 1) * settings.sessionCount
      private val backpressure = new Semaphore(bufferSize)

      override protected def createSession(
        connection: Connection,
        createDestination: Session => Destination
      ): JmsConsumerSession = {

        val session = connection.createSession(false, AcknowledgeMode.AutoAcknowledge.mode)

        // TODO: avoid get
        new JmsConsumerSession(
          connection, session, settings.jmsDestination.get
        )
      }

      override protected def pushMessage(msg: FlowMessage): Unit = {
        push(out, msg)
        backpressure.release()
      }

      override protected def onSessionOpened(jmsSession: JmsConsumerSession): Unit = {

        log.debug(s"Creating JMS consumer for destination [${jmsSettings.jmsDestination.get.name}]")

        jmsSession.createConsumer(settings.selector).onComplete {
          case Success(consumer) =>
            consumer.setMessageListener(new MessageListener {
              override def onMessage(message: Message): Unit = {
                backpressure.acquire()
                handleMessage.invoke(JmsSourceStage.jms2flowMessage(message))
              }
            })
          case Failure(t) =>
            fail.invoke(t)
        }
      }
    }

    (logic, logic.killSwitch)
  }

}
