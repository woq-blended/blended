package blended.streams.jms

import java.util.concurrent.Semaphore

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue}
import blended.jms.utils.{JmsAckSession, JmsDestination}
import blended.streams.message.{FlowEnvelope, JmsAckEnvelope}
import blended.util.logging.Logger
import javax.jms._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Success}


final class JmsAckSourceStage(settings: JMSConsumerSettings, actorSystem : ActorSystem)
  extends GraphStageWithMaterializedValue[SourceShape[FlowEnvelope], KillSwitch] {

  private val out = Outlet[FlowEnvelope]("JmsSource.out")

  override def shape: SourceShape[FlowEnvelope] = SourceShape[FlowEnvelope](out)

  override protected def initialAttributes: Attributes =
    ActorAttributes.dispatcher("FixedPool")

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, KillSwitch) = {

    val logic = new SourceStageLogic[JmsAckSession](shape, out, settings, inheritedAttributes) {

      private[this] val log = Logger(classOf[JmsAckSourceStage].getName())
      private[this] val pendingAcks : mutable.Map[String, Semaphore] = mutable.Map.empty
      private[this] val inflight : mutable.Map[String, JmsAckEnvelope] = mutable.Map.empty

      private val acknowledge : JmsAckEnvelope => Unit = { env =>
        log.debug(s"Acknowledging message for session id [${env.session.sessionId}] : ${env.flowMessage}")
        try {
          // the acknowlede might not work a message might be in inflight while the previous message has not been
          // acknowledged. In this case we will have closed the session and created a new one.
          env.jmsMsg.acknowledge()
        } catch {
          case e: JMSException =>
        } finally {
          pendingAcks.get(env.session.sessionId).foreach { s =>
            s.release()
          }
          inflight -= env.session.sessionId
        }
      }

      private[this] def ackQueued(): Unit = {

        inflight.foreach { case (sessionId, envelope) =>
          Option(envelope.session.ackQueue.poll()) match {
            case Some(action) =>
              action match {
                case Right(sessionId) =>
                  inflight.get(sessionId).foreach(acknowledge)
                  ackQueued()

                case Left(t) =>
                  failStage(t)
              }
            case None =>
              if (System.currentTimeMillis() - envelope.created > jmsSettings.ackTimeout.toMillis) {
                log.debug(s"Acknowledge timed out for [$sessionId] with message ${envelope.flowMessage}")
                inflight -= sessionId
                // Recovering the session, so that unacknowledged messages may be redelivered
                closeSession(envelope.session)
                // Finally, release the semaphore, so that the session may consume more messages
                pendingAcks.get(sessionId).foreach(_.release())
              }
          }
        }
      }

      override def preStart(): Unit = {
        super.preStart()
        schedulePeriodically("Ack", 10.millis)
      }

      override def postStop(): Unit = {
        super.postStop()
        cancelTimer("Ack")
      }


      override protected def createSession(connection: Connection): JmsAckSession = {

        jmsSettings.jmsDestination match {
          case Some(d) =>
            val session = connection.createSession(false, AcknowledgeMode.ClientAcknowledge.mode)
            new JmsAckSession(
              connection = connection,
              session = session,
              sessionId = nextSessionId,
              jmsDestination = d
            )
          case None =>
            val msg = s"Destination must be set for consumer in [$id]"
            log.error(msg)
            throw new IllegalArgumentException(msg)
        }
      }

      private[this] def closeSession(session: JmsAckSession) : Unit = {

        try {
          session.closeSessionAsync().onComplete { _ =>
            jmsSessions = jmsSessions.filter(_ != session.sessionId)
            onSessionClosed()
          }
        } catch {
          case _ : Throwable =>
            log.error(s"Error closing session with id [${session.sessionId}]")
        }
      }

      private[this] def onSessionClosed() : Unit = initSessionAsync()

      override protected def onTimer(timerKey: Any): Unit = ackQueued()

      private val dest : JmsDestination = jmsSettings.jmsDestination match {
        case Some(d) => d
        case None => throw new Exception("Destination must be set for Consumer")
      }

      protected def pushMessage(msg: FlowEnvelope): Unit = {
        push(out, msg)
      }

      override protected def onSessionOpened(jmsSession: JmsAckSession): Unit =

        jmsSession match {
          case session: JmsAckSession =>
            session.createConsumer(settings.selector).onComplete {
              case Success(consumer) =>

                consumer.setMessageListener(new MessageListener {

                  def onMessage(message: Message): Unit = {
                    val flowMessage = JmsFlowMessage.jms2flowMessage(jmsSettings, message)
                    log.debug(s"Message received for [${session.sessionId}] : $flowMessage")
                    try {

                      val semaphore : Semaphore = pendingAcks.get(session.sessionId) match {
                        case Some(s) => s
                        case None =>
                          val s = new Semaphore(1)
                          pendingAcks += (session.sessionId -> s)
                          s
                      }

                      // Aquire the semaphore and memorize the inflight message
                      semaphore.acquire()
                      val envelope = JmsAckEnvelope(flowMessage, message, session, System.currentTimeMillis())
                      inflight += (session.sessionId -> envelope)
                      handleMessage.invoke(envelope)

                    } catch {
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
