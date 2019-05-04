package blended.streams.jms

import java.util.UUID
import java.util.concurrent.Semaphore

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage._
import blended.jms.utils.{JmsConsumerSession, JmsDestination}
import blended.streams.message._
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger
import javax.jms._

import scala.util.{Failure, Success}

class JmsSourceStage(
  name : String,
  settings: JMSConsumerSettings,
  log : Logger = Logger[JmsSourceStage]
)(implicit actorSystem: ActorSystem) extends GraphStage[SourceShape[FlowEnvelope]] {

  private val out = Outlet[FlowEnvelope](s"JmsSource($name.out)")

  private val headerConfig : FlowHeaderConfig = settings.headerCfg

  override def shape: SourceShape[FlowEnvelope] = SourceShape(out)

  override protected def initialAttributes: Attributes =
    ActorAttributes.dispatcher("FixedPool")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =  {

    val logic : GraphStageLogic = new SourceStageLogic[JmsConsumerSession](shape, out, settings, inheritedAttributes) {

      private val bufferSize = (settings.bufferSize + 1) * settings.sessionCount
      private val backpressure = new Semaphore(bufferSize)

      override private[jms] val handleError = getAsyncCallback[Throwable]{ ex =>
        fail(out, ex)
      }

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
        log.trace("Pushing message downstream")
        push(out, msg)
        backpressure.release()
      }

      override protected def onSessionOpened(jmsSession: JmsConsumerSession): Unit = {

        log.debug(s"Creating JMS consumer in [$id] for destination [$dest]")

        jmsSession.createConsumer(settings.selector) match {
          case Success(consumer) =>
            try {
              consumer.setMessageListener(new MessageListener {
                override def onMessage(message: Message): Unit = {
                  backpressure.acquire()
                  // Use a Default Envelope that simply ignores calls to acknowledge if any
                  val flowMessage = JmsFlowSupport.jms2flowMessage(headerConfig)(jmsSettings)(message).get

                  val envelopeId : String = flowMessage.header[String](headerConfig.headerTransId) match {
                    case None =>
                      val newId = UUID.randomUUID().toString()
                      log.trace(s"Created new envelope id [$newId]")
                      newId
                    case Some(s) =>
                      log.trace(s"Reusing transaction id [$s] as envelope id")
                      s
                  }

                  log.debug(s"Message received for [$envelopeId][${settings.jmsDestination.map(_.asString)}] [$id] : $flowMessage")

                  handleMessage.invoke(
                    FlowEnvelope(
                      flowMessage.withHeader(headerConfig.headerTransId, envelopeId).get, envelopeId
                    )
                  )
                }
              })
            } catch {
              case jmse : JMSException =>
                log.warn(jmse)(s"Error setting up message listener [${settings.jmsDestination}] in [${jmsSession.sessionId}]")
                closeSession(jmsSession)
            }

          case Failure(t) =>
            log.warn(t)(s"Error setting up consumer [${settings.jmsDestination}] in [${jmsSession.sessionId}]")
            closeSession(jmsSession)
        }
      }
    }

    logic
  }

}
