package blended.streams.jms

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic}
import blended.jms.utils.{JmsAckSession, JmsAckState, JmsDestination, Reconnect}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.FlowHeaderConfig
import javax.jms._

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * The JmsAckSourceStage realizes an inbound Stream of FlowMessages consumed from
  * a given JMS destination. The messages can be consumed using an arbitrary sink
  * of FlowMessages. The sink must acknowledge or deny the message eventually. If
  * the message is not acknowledged with a given time frame, the message will be
  * denied and eventually redelivered.
  *
  * A JMS session will only consume one message at a time, the next message will
  * be consumed only after the previous message of that session has been denied
  * or acknowledged. The messages that are waiting for acknowldgement are maintained
  * within a map of (session-id / inflight messages).
  *
  * Any exception while consuming a message within a session will cause that session
  * to close, so that any inflight messages for that session will be redelivered. A
  * new session will be created automatically for any sessions that have been closed
  * with such an exception.
  *
  * One of the use cases is a retry pattern. In that pattern the message must remain
  * in the underlying destination for a minimum amount of time (i.e. 5 seconds).
  * The message receive loop will check the JMSTimestamp against the System.currentTimeMillis
  * to check wether a message can be passed downstream already.
  **/
final class JmsAckSourceStage(
  name : String,
  settings: JMSConsumerSettings,
  minMessageDelay : Option[FiniteDuration] = None
)(implicit system : ActorSystem)
  extends GraphStage[SourceShape[FlowEnvelope]] {

  sealed trait TimerEvent
  private case class Ack(s : String) extends TimerEvent
  private case class Poll(s : String) extends TimerEvent

  private val headerConfig : FlowHeaderConfig = settings.headerCfg

  private val out = Outlet[FlowEnvelope](s"JmsAckSource($name.out)")

  override def shape: SourceShape[FlowEnvelope] = SourceShape[FlowEnvelope](out)

  // TODO: Refactor to clean up code
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {

    val logic : GraphStageLogic = new SourceStageLogic[JmsAckSession](shape, out, settings, inheritedAttributes) {

      private[this] var inflight : Map[String, FlowEnvelope] = Map.empty
      private[this] var consumer : Map[String, MessageConsumer] = Map.empty
      private[this] var nextPoll : Option[Long] = None

      private[this] def addInflight(s : String, env: FlowEnvelope) : Unit = {
        inflight = inflight + (s -> env)
        settings.log.debug(s"Inflight message count of [$id] count is [${inflight.size}]")
      }

      private[this] def removeInflight(s : String) : Unit = {
        inflight = inflight.filterKeys(_!= s)
        settings.log.debug(s"Inflight message count of [$id] count is [${inflight.size}]")
      }

      private[this] def addConsumer(s: String, c : MessageConsumer) : Unit = {
        consumer = consumer + (s -> c)
        settings.log.debug(s"Consumer count of [$id] is [${consumer.size}]")
      }

      private[this] def removeConsumer(s : String) : Unit = {
        consumer = consumer.filterKeys(_ != s)
        cancelTimer(Poll(s))
        settings.log.debug(s"Consumer count of [$id] is [${consumer.size}]")
      }

      private val ackHandler : FlowEnvelope => Option[JmsAcknowledgeHandler] = { env =>
        env.ackHandler match {
          case None => None
          case Some(h) if h.isInstanceOf[JmsAcknowledgeHandler] => Some(h.asInstanceOf[JmsAcknowledgeHandler])
          case _ => None
        }
      }

      override protected def afterSessionClose(session: JmsAckSession): Unit = removeConsumer(session.sessionId)

      // This is to actually perform the acknowledement if one is pending
      private val acknowledge : FlowEnvelope => JmsAcknowledgeHandler => Try[Unit] = env => handler => Try {

        val sessionId = handler.session.sessionId

        handler.session.ackState match {

          case JmsAckState.Acknowledged =>
            try {
              handler.jmsMessage.acknowledge()
              settings.log.debug(s"Acknowledged envelope [${env.id}] message for session [$sessionId]")
              removeInflight(sessionId)
              poll(sessionId)
            } catch {
              case t: Throwable =>
                settings.log.error(t)(s"Failed to acknowledge message [${env.id}] for session [$sessionId]")
                closeSession(handler.session)
            }

          case JmsAckState.Denied =>
            settings.log.debug(s"Denying message [${env.id}] for session [$sessionId]")
            closeSession(handler.session)

          case JmsAckState.Pending =>
            if (System.currentTimeMillis() - handler.created > jmsSettings.ackTimeout.toMillis) {
              settings.log.warn(s"Acknowledge timed out for message [${env.id}] in session [$sessionId]")
              closeSession(handler.session)
            } else {
              scheduleOnce(Ack(sessionId), 10.millis)
            }
        }
      }

      private[this] def ackQueued(s : String): Unit = {
        inflight.get(s).foreach { env =>
          ackHandler(env).foreach { handler =>
            acknowledge(env)(handler).get
          }
        }
      }

      private[this] def poll(sid : String) : Unit = {

        def receive(sid: String) : (Option[Message], FiniteDuration) = {

          (jmsSessions.get(sid), consumer.get(sid)) match {
            case (Some(session), Some(c)) =>

              val msg : Option[Message] = if (settings.receiveTimeout.toMillis <= 0) {
                Option(c.receiveNoWait())
              } else {
                Option(c.receive(settings.receiveTimeout.toMillis))
              }

              val result : (Option[Message], FiniteDuration) = msg match {
                case None =>
                  (None, settings.pollInterval)

                case Some(m) =>
                  minMessageDelay match {
                    case Some(d) =>
                      if (System.currentTimeMillis() - m.getJMSTimestamp() <= d.toMillis) {
                        settings.log.trace(s"Message has not reached the minimum message delay yet ...")
                        closeSession(session)
                        nextPoll = Some(m.getJMSTimestamp() + d.toMillis)
                        (None, d)
                      } else {
                        nextPoll = None
                        (Some(m), settings.pollInterval)
                      }
                    case None =>
                      (msg, settings.pollInterval)
                  }
              }

              result
            case (_, _) =>
              settings.log.trace(s"Session or consumer not available in [$sid]")
              (None, 100.millis)
          }
        }

        settings.log.trace(s"Trying to receive message from [${settings.jmsDestination.map(_.asString)}] in session [${sid}]")

        (jmsSessions.get(sid), consumer.get(sid)) match {
          case (Some(session), Some(c)) =>

          try {
            receive(sid) match {
              case (Some(message), _) =>
                val flowMessage : FlowMessage = JmsFlowSupport.jms2flowMessage(headerConfig)(jmsSettings)(message).get

                val envelopeId: String = flowMessage.header[String](headerConfig.headerTransId) match {
                  case None =>
                    val newId = UUID.randomUUID().toString()
                    settings.log.trace(s"Created new envelope id [$newId]")
                    newId
                  case Some(s) =>
                    settings.log.trace(s"Reusing transaction id [$s] as envelope id")
                    s
                }

                settings.log.log(
                  settings.receiveLogLevel,
                  s"Message received [$envelopeId][${settings.jmsDestination.map(_.asString)}][${session.sessionId}] : $flowMessage"
                )

                val handler = JmsAcknowledgeHandler(
                  id = envelopeId,
                  jmsMessage = message,
                  session = session,
                  log = settings.log
                )

                val envelope = FlowEnvelope(flowMessage, envelopeId)
                  .withHeader(headerConfig.headerTransId, envelopeId).get
                  .withRequiresAcknowledge(true)
                  .withAckHandler(Some(handler))

                session.resetAck()
                addInflight(session.sessionId, envelope)
                handleMessage.invoke(envelope)
                ackQueued(sid)
              case (None, nextPoll) =>
                settings.log.trace(s"No message available for [${session.sessionId}]")
                scheduleOnce(Poll(sid), nextPoll)
            }
          } catch {
            case e: JMSException =>
              settings.log.warn(s"Error receiving message : [${e.getMessage()}]")
              closeSession(session)
          }

          case (_, _) =>
            settings.log.trace(s"Session or consumer not available in [$sid]")
            scheduleOnce(Poll(sid), 100.millis)
        }
      }

      override protected def createSession(connection: Connection): Try[JmsAckSession] = {

        try {
          jmsSettings.jmsDestination match {
            case Some(d) =>
              val session = connection.createSession(false, AcknowledgeMode.ClientAcknowledge.mode)
              Success(new JmsAckSession(
                connection = connection,
                session = session,
                sessionId = nextSessionId,
                jmsDestination = d
              ))
            case None =>
              val msg = s"Destination must be set for consumer in [$id]"
              settings.log.error(msg)
              throw new IllegalArgumentException(msg)
          }
        } catch {
          case NonFatal(t) =>
            jmsSettings.log.error(s"Error creating JMS session : [$t.]")
            handleError.invoke(t)
            Failure(t)
        }
      }

      override protected def handleTimer: PartialFunction[Any, Unit] = super.handleTimer orElse {
        case Ack(s) =>
          ackQueued(s)

        case p : Poll =>
          nextPoll match {
            case None => poll(p.s)

            case Some(l) =>
              val now : Long = System.currentTimeMillis()
              if (now >= l) {
                poll(p.s)
              } else {
                val delay : FiniteDuration = (l - now).millis
                settings.log.trace(s"Delaying msg poll by [$delay] for session [${p.s}]")
                scheduleOnce(p, (l - now).millis)
              }
          }
        case _ =>
      }

      private val dest : JmsDestination = jmsSettings.jmsDestination match {
        case Some(d) => d
        case None => throw new Exception("Destination must be set for Consumer")
      }

      protected def pushMessage(msg: FlowEnvelope): Unit = {
        push(out, msg)
      }

      private[this] def createConsumer(session: JmsAckSession) : Unit = {
        settings.log.debug(s"Creating message consumer for session [${session.sessionId}], destination [$dest] and selector [${settings.selector}]")
        session.createConsumer(settings.selector) match {

          case Success(c) =>
            addConsumer(session.sessionId, c)
            scheduleOnce(Poll(session.sessionId), 100.millis)

          case Failure(e) =>
            settings.log.debug(s"Failed to create consumer for session [${session.sessionId}]")
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
