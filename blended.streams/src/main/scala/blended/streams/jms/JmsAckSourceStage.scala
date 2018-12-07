package blended.streams.jms

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic}
import blended.jms.utils.{JmsAckSession, JmsDestination}
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger
import javax.jms._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

final class JmsAckSourceStage(
  name : String,
  settings: JMSConsumerSettings,
  headerConfig : FlowHeaderConfig,
  log: Logger
)(implicit system : ActorSystem)
  extends GraphStage[SourceShape[FlowEnvelope]] {

  sealed trait TimerEvent
  private case class Ack(s : String) extends TimerEvent
  private case class Poll(s : String) extends TimerEvent

  private val out = Outlet[FlowEnvelope](s"JmsAckSource($name.out)")

  override def shape: SourceShape[FlowEnvelope] = SourceShape[FlowEnvelope](out)

  override protected def initialAttributes: Attributes =
    ActorAttributes.dispatcher("FixedPool")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {

    val logic = new SourceStageLogic[JmsAckSession](shape, out, settings, inheritedAttributes, log) {

      private[this] var inflight : Map[String, FlowEnvelope] = Map.empty
      private[this] var consumer : Map[String, MessageConsumer] = Map.empty

      private[this] def addInflight(s : String, env: FlowEnvelope) : Unit = {
        inflight = inflight + (s -> env)
        log.debug(s"Inflight message count is [${inflight.size}]")
      }

      private[this] def removeInflight(s : String) : Unit = {
        inflight = inflight.filterKeys(_!= s)
        log.debug(s"Inflight message of [$id] count is [${inflight.size}]")
      }

      private[this] def addConsumer(s: String, c : MessageConsumer) : Unit = {
        consumer = consumer + (s -> c)
        log.debug(s"Consumer count of [$id] is [${consumer.size}]")
      }

      private[this] def removeConsumer(s : String) : Unit = {
        consumer = consumer.filterKeys(_ != s)
        log.debug(s"Consumer count of [$id] is [${consumer.size}]")
      }

      override private[jms] val handleError = getAsyncCallback[Throwable]{ ex =>
        log.error(ex)(ex.getMessage())
        failStage(ex)
      }

      private val ackHandler : FlowEnvelope => Option[JmsAcknowledgeHandler] = { env =>
        env.ackHandler match {
          case None => None
          case Some(h) if h.isInstanceOf[JmsAcknowledgeHandler] => Some(h.asInstanceOf[JmsAcknowledgeHandler])
          case _ => None
        }
      }

      // This is to actually perform the acknowledement if one is pending
      private val acknowledge : JmsAcknowledgeHandler => Try[Unit] = handler => Try {

        val sessionId = handler.session.sessionId

        if (handler.session.acknowledged.get()) {
          try {
            handler.jmsMessage.acknowledge()
            log.trace(s"Acknowledged JMS message for session [$sessionId]")
            removeInflight(sessionId)
            scheduleOnce(Poll(sessionId), 10.millis)
          } catch {
            case t: Throwable =>
              log.error(t)(s"Failed to acknowledge message for sesseion [$sessionId]")
              removeInflight(sessionId)
              closeSession(handler.session)
          }
        } else {
          if (System.currentTimeMillis() - handler.created > jmsSettings.ackTimeout.toMillis) {
            log.warn(s"Acknowledge timed out for [$sessionId]")
            removeInflight(sessionId)
            // Recovering the session, so that unacknowledged messages may be redelivered
            closeSession(handler.session)
          } else {
            scheduleOnce(Ack(sessionId), 10.millis)
          }
        }
      }

      private[this] def ackQueued(s : String): Unit = {
        inflight.get(s).foreach { env =>
          ackHandler(env).foreach { handler =>
            acknowledge(handler).get
          }
        }
      }

      private[this] def poll(sid : String) : Unit = {

        log.trace(s"Trying to receive message from [${settings.jmsDestination.map(_.asString)}] in session [${sid}]")

        (jmsSessions.get(sid), consumer.get(sid)) match {
          case (Some(session), Some(c)) =>

          try {
            // TODO: Make the receive timeout configurable
            Option(c.receiveNoWait()) match {
              case Some(message) =>
                val flowMessage = JmsFlowSupport.jms2flowMessage(headerConfig)(jmsSettings)(message).get
                log.debug(s"Message received [${settings.jmsDestination.map(_.asString)}] [${session.sessionId}] : ${flowMessage.header.mkString(",")}")

                val envelopeId: String = flowMessage.header[String](headerConfig.headerTrans) match {
                  case None =>
                    val newId = UUID.randomUUID().toString()
                    log.debug(s"Created new envelope id [$newId]")
                    newId
                  case Some(s) =>
                    log.debug(s"Reusing transaction id [$s] as envelope id")
                    s
                }

                val handler = JmsAcknowledgeHandler(
                  id = envelopeId,
                  jmsMessage = message,
                  session = session,
                  log = log
                )

                val envelope = FlowEnvelope(flowMessage, envelopeId)
                  .withHeader(headerConfig.headerTrans, envelopeId).get
                  .withRequiresAcknowledge(true)
                  .withAckHandler(Some(handler))

                session.resetAck()
                addInflight(session.sessionId, envelope)
                handleMessage.invoke(envelope)
                scheduleOnce(Ack(sid), 10.millis)
              case None =>
                log.trace(s"No message available for [${session.sessionId}]")
                scheduleOnce(Poll(sid), 100.millis)
            }
          } catch {
            case e: JMSException =>
              log.warn(s"Error receiving message : [${e.getMessage()}]")
              closeSession(session)
          }

          case (_, _) =>
            log.trace(s"Session or consumer not available in [$sid]")
            scheduleOnce(Poll(sid), 100.millis)
        }
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
          log.debug(s"Closing session [${session.sessionId}]")
          session.closeSessionAsync().onComplete { _ =>
            removeConsumer(session.sessionId)
            jmsSessions -= session.sessionId
            onSessionClosed()
          }
        } catch {
          case _ : Throwable =>
            log.error(s"Error closing session with id [${session.sessionId}]")
        }
      }

      private[this] def onSessionClosed() : Unit = initSessionAsync()

      override protected def onTimer(timerKey: Any): Unit = {

        timerKey match {
          case Ack(s) =>
            ackQueued(s)
          case p : Poll =>
            poll(p.s)
          case _ =>
        }
      }

      private val dest : JmsDestination = jmsSettings.jmsDestination match {
        case Some(d) => d
        case None => throw new Exception("Destination must be set for Consumer")
      }

      protected def pushMessage(msg: FlowEnvelope): Unit = {
        push(out, msg)
      }

      private[this] def createConsumer(session: JmsAckSession) : Unit = {
        log.debug(s"Creating message consumer for session [${session.sessionId}] and destination [$dest]")
        session.createConsumer(settings.selector) match {

          case Success(c) =>
            addConsumer(session.sessionId, c)
            scheduleOnce(Poll(session.sessionId), 100.millis)

          case Failure(e) =>
            log.debug(s"Failed to create consumer for session [${session.sessionId}]")
            closeSession(session)
        }
      }

      override protected def onSessionOpened(jmsSession: JmsAckSession): Unit =

        jmsSession match {
          case session: JmsAckSession =>
            createConsumer(session)
          case _ =>
            throw new IllegalArgumentException(
              "Session must be of type JMSAckSession, it is a " + jmsSession.getClass.getName
            )
        }
    }

    logic
  }
}
