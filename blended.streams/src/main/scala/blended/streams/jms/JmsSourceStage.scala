package blended.streams.jms

import java.util.concurrent.Semaphore

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage._
import blended.jms.utils.{JmsConsumerSession, JmsDestination}
import blended.streams.message._
import javax.jms._

import scala.util.{Failure, Success}

class JmsSourceStage(settings: JMSConsumerSettings, actorSystem: ActorSystem) extends GraphStageWithMaterializedValue[SourceShape[FlowEnvelope], KillSwitch] {

  private val out = Outlet[FlowEnvelope]("JmsSource.out")

  override def shape: SourceShape[FlowEnvelope] = SourceShape(out)

  override protected def initialAttributes: Attributes =
    ActorAttributes.dispatcher("FixedPool")

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, KillSwitch) = {

    val logic = new SourceStageLogic[JmsConsumerSession](shape, out, settings, inheritedAttributes) {

      private val bufferSize = (settings.bufferSize + 1) * settings.sessionCount
      private val backpressure = new Semaphore(bufferSize)

      private val dest : JmsDestination = jmsSettings.jmsDestination match {
        case Some(d) => d
        case None => throw new IllegalArgumentException("Destination must be defined for consumer")
      }

      override protected def createSession(
        connection: Connection,
      ): JmsConsumerSession = {

        val session = connection.createSession(false, AcknowledgeMode.AutoAcknowledge.mode)
        new JmsConsumerSession(
          connection = connection,
          session = session,
          sessionId = nextSessionId(),
          jmsDestination = dest
        )
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
                val flowMessage = JmsFlowMessage.jms2flowMessage(message)
                log.debug(s"Message received for [$id] : $flowMessage")
                handleMessage.invoke(DefaultFlowEnvelope(flowMessage))
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
