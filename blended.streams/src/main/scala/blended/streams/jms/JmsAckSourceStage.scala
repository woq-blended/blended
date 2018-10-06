package blended.streams.jms

import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue}
import akka.stream.{Attributes, KillSwitch, Outlet, SourceShape}
import blended.jms.utils.{JmsAckSession, JmsConsumerSession, JmsDestination, StopMessageListenerException}
import blended.streams.message.{DefaultFlowEnvelope, FlowEnvelope, JmsAckEnvelope}
import javax.jms._

import scala.annotation.tailrec
import scala.util.{Failure, Success}

final class JmsAckSourceStage(settings: JMSConsumerSettings)
  extends GraphStageWithMaterializedValue[SourceShape[FlowEnvelope], KillSwitch] {

  private val out = Outlet[FlowEnvelope]("JmsSource.out")

  override def shape: SourceShape[FlowEnvelope] = SourceShape[FlowEnvelope](out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, KillSwitch) = {

    val logic = new SourceStageLogic(shape, out, settings, inheritedAttributes) {

      private val dest : JmsDestination = jmsSettings.jmsDestination match {
        case Some(d) => d
        case None => throw new Exception("Destination must be set for Consumer")
      }

      private val maxPendingAck = settings.bufferSize

      protected def createSession(
        connection: Connection,
        createDestination: Session => javax.jms.Destination
      ): JmsAckSession = {

        val session =
          connection.createSession(false, AcknowledgeMode.ClientAcknowledge.mode)

        new JmsAckSession(
          connection,
          session,
          dest
        )
      }

      protected def pushMessage(msg: FlowEnvelope): Unit = push(out, msg)

      override protected def onSessionOpened(jmsSession: JmsConsumerSession): Unit =

        jmsSession match {
          case session: JmsAckSession =>
            session.createConsumer(settings.selector).onComplete {
              case Success(consumer) =>
                consumer.setMessageListener(new MessageListener {

                  var listenerStopped = false

                  def onMessage(message: Message): Unit = {

                    @tailrec
                    def ackQueued(): Unit =
                      Option(session.ackQueue.poll()) match {
                        case Some(action) =>
                          try {
                            action()
                            session.pendingAck -= 1
                          } catch {
                            case _: StopMessageListenerException =>
                              listenerStopped = true
                          }
                          if (!listenerStopped) ackQueued()
                        case None =>
                      }

                    if (!listenerStopped)
                      try {
                        handleMessage.invoke(JmsAckEnvelope(JmsFlowMessage.jms2flowMessage(message), message, session))
                        session.pendingAck += 1
                        if (session.pendingAck > maxPendingAck) {
                          val action = session.ackQueue.take()
                          action()
                          session.pendingAck -= 1
                        }
                        ackQueued()
                      } catch {
                        case _: StopMessageListenerException =>
                          listenerStopped = true
                        case e: JMSException =>
                          handleError.invoke(e)
                      }
                  }
                })
              case Failure(e) =>
                fail.invoke(e)
            }

          case _ =>
            throw new IllegalArgumentException(
              "Session must be of type JMSAckSession, it is a " + jmsSession.getClass.getName
            )
        }
    }

    (logic, logic.killSwitch)
  }
}
