package blended.streams.jms

import java.util.concurrent.Semaphore

import akka.stream.stage._
import akka.stream.{Attributes, KillSwitch, Outlet, SourceShape}
import akka.util.ByteString
import blended.jms.utils.{JmsConsumerSession, JmsDestination}
import blended.streams.message._
import javax.jms._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

object JmsSourceStage {

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

class JmsSourceStage(settings: JMSConsumerSettings) extends GraphStageWithMaterializedValue[SourceShape[FlowEnvelope], KillSwitch] {

  private val out = Outlet[FlowEnvelope]("JmsSource.out")

  override def shape: SourceShape[FlowEnvelope] = SourceShape(out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, KillSwitch) = {

    val logic = new SourceStageLogic(shape, out, settings, inheritedAttributes) {

      private val bufferSize = (settings.bufferSize + 1) * settings.sessionCount
      private val backpressure = new Semaphore(bufferSize)

      private val dest : JmsDestination = jmsSettings.jmsDestination match {
        case Some(d) => d
        case None => throw new JMSException("Destination must be defined for consumer")
      }

      override protected def createSession(
        connection: Connection,
        createDestination: Session => Destination
      ): JmsConsumerSession = {

        val session = connection.createSession(false, AcknowledgeMode.AutoAcknowledge.mode)

        new JmsConsumerSession(connection, session, dest)
      }

      override protected def pushMessage(msg: FlowEnvelope): Unit = {
        push(out, msg)

        backpressure.release()
      }

      override protected def onSessionOpened(jmsSession: JmsConsumerSession): Unit = {

        log.debug(s"Creating JMS consumer in [$id] for destination [$dest]")

        jmsSession.createConsumer(settings.selector).onComplete {
          case Success(consumer) =>
            consumer.setMessageListener(new MessageListener {
              override def onMessage(message: Message): Unit = {
                backpressure.acquire()
                // Use a Default Envelope that simply ignores calls to acknowledge if any
                handleMessage.invoke(DefaultFlowEnvelope(JmsSourceStage.jms2flowMessage(message)))
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
