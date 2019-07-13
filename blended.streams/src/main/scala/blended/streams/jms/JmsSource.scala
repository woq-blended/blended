package blended.streams.jms

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, TimerGraphStageLogic}
import blended.jms.utils.{JmsDestination, JmsSession}
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope, FlowMessage}
import blended.streams.transaction.FlowHeaderConfig
import blended.streams.{AckSourceLogic, DefaultAcknowledgeContext}
import blended.util.RichTry._
import blended.util.logging.Logger
import javax.jms.{JMSException, Message, MessageConsumer}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * The JmsSource realizes an inbound Stream of FlowMessages consumed from
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
 */
final class JmsSource(
  name : String,
  settings : JMSConsumerSettings,
  minMessageDelay : Option[FiniteDuration] = None,
  autoAcknowledge : Boolean = false
)(implicit actorSystem : ActorSystem)
  extends GraphStage[SourceShape[FlowEnvelope]] {

  sealed trait TimerEvent
  private case class Ack(s : String) extends TimerEvent
  private case class Poll(s : String) extends TimerEvent

  private val headerConfig : FlowHeaderConfig = settings.headerCfg

  private val outlet : Outlet[FlowEnvelope] = Outlet[FlowEnvelope](s"JmsAckSource($name.out)")

  override def shape : SourceShape[FlowEnvelope] = SourceShape[FlowEnvelope](outlet)

  private class JmsAckContext(
    inflightId : String,
    env : FlowEnvelope,
    val jmsMessage : Message,
    val session : JmsSession
  ) extends DefaultAcknowledgeContext(inflightId, env, System.currentTimeMillis())

  private class JmsSourceLogic extends TimerGraphStageLogic(shape)
    with JmsConnector
    with AckSourceLogic[JmsAckContext] {

    override protected def system: ActorSystem = actorSystem
    override protected def jmsSettings: JmsSettings = settings
    override protected def log: Logger = settings.log
    override def out: Outlet[FlowEnvelope] = outlet

    private val srcDest : JmsDestination = settings.jmsDestination match {
      case Some(d) => d
      case None => throw new IllegalArgumentException(s"Destination must be set for consumer in [$id]")
    }

    /** The id's of the available inflight slots */
    override protected def inflightSlots(): List[String] =
      1.to(settings.sessionCount).map(i => s"$id-$i").toList

    private var nextPollRelative : Option[FiniteDuration] = None
    override protected def nextPoll(): Option[FiniteDuration] =
      Some(nextPollRelative.getOrElse(settings.pollInterval))

    override protected val onSessionOpened : JmsSession => Try[Unit] = session => Try {
      settings.log.debug(s"Creating message consumer for session [${session.sessionId}], destination [$srcDest] and selector [${settings.selector}]")
      session.createConsumer(srcDest, settings.selector) match {

        case Success(c) =>
          addConsumer(session.sessionId, c)

        case Failure(e) =>
          settings.log.debug(s"Failed to create consumer for session [${session.sessionId}] : [${e.getMessage()}]")
          sessionMgr.closeSession(session.sessionId)
      }
    }

    override protected val beforeSessionCloseCallback : JmsSession => Try[Unit] = s => Try {
      consumer.get(s.sessionId).foreach{ c =>
        settings.log(s"Closing Consumer for [$s]")
        c.close()
        removeConsumer(s.sessionId)
      }
    }

    private[this] val consumer : mutable.Map[String, MessageConsumer] = mutable.Map.empty

    private[this] def addConsumer(s : String, c : MessageConsumer) : Unit = {
      consumer.put(s, c)
      settings.log.debug(s"Jms Consumer count of [$id] is [${consumer.size}]")
    }

    private[this] def removeConsumer(s : String) : Unit = {
      if (consumer.contains(s)) {
        consumer.remove(s)
        settings.log.debug(s"Consumer count of [$id] is [${consumer.size}]")
      }
    }

    private def receive(session : JmsSession, c : MessageConsumer) : Try[Option[Message]] = Try {

      val msg : Option[Message] = if (settings.receiveTimeout.toMillis <= 0) {
        Option(c.receiveNoWait())
      } else {
        Option(c.receive(settings.receiveTimeout.toMillis))
      }

      val result : Option[Message] = msg match {
        case None => None

        case Some(m) =>
          minMessageDelay match {
            case Some(d) =>
              val remainingDelay : Long = System.currentTimeMillis() - m.getJMSTimestamp()
              if (remainingDelay <= d.toMillis) {
                settings.log.trace(s"Message has not reached the minimum message delay yet ...")
                sessionMgr.closeSession(id)
                nextPollRelative = Some(remainingDelay.millis)
                None
              } else {
                nextPollRelative = None
                Some(m)
              }
            case None =>
              msg
          }
      }

      result
    }

    private def createEnvelope(message : Message, ackHandler : AcknowledgeHandler) : Try[FlowEnvelope] = Try {

      val flowMessage : FlowMessage = JmsFlowSupport.jms2flowMessage(headerConfig)(settings)(message).unwrap

      val envelopeId : String = flowMessage.header[String](headerConfig.headerTransId) match {
        case None =>
          val newId = UUID.randomUUID().toString()
          settings.log.trace(s"Created new envelope id [$newId]")
          newId
        case Some(s) =>
          settings.log.trace(s"Reusing transaction id [$s] as envelope id")
          s
      }

      if (!autoAcknowledge) {
        FlowEnvelope(flowMessage, envelopeId)
          .withHeader(headerConfig.headerTransId, envelopeId).unwrap
          .withRequiresAcknowledge(true)
          .withAckHandler(Some(ackHandler))
      } else {
        message.acknowledge()
        FlowEnvelope(flowMessage, envelopeId)
          .withHeader(headerConfig.headerTransId, envelopeId).unwrap
          .withRequiresAcknowledge(false)
      }
    }

    override protected def doPerformPoll(id: String, ackHandler: AcknowledgeHandler): Try[Option[JmsAckContext]] = Try {

      settings.log.trace(s"Trying to receive message from [${srcDest.asString}] in session [$id]")
      None

//      sessionMgr.getSession(id) match {
//        case Success(Some(sess)) =>
//          consumer.get(id).map { c =>
//            receive(sess, c).unwrap.map { message =>
//              createEnvelope(message, ackHandler).map { e =>
//
//                settings.log.info(
//                  s"Message received [${e.id}][${srcDest.asString}][${sess.sessionId}] : ${e.flowMessage}"
//                )
//
//                new JmsAckContext(
//                  inflightId = id,
//                  env = e,
//                  jmsMessage = message,
//                  session = sess
//                )
//              }
//            }
//          }
//        case Success(None) =>
//          log.trace(s"No session available in [$id]")
//          None
//        case Failure(t) =>
//          handleError.invoke(t)
//          throw t
//      }
    }

    override protected def beforeDenied(ackCtxt: JmsAckContext): Unit = {
      log.info(s"Message [${ackCtxt.envelope.id}] has been denied. Closing receiving session.")
      sessionMgr.closeSession(ackCtxt.session.sessionId)
    }

    override protected def beforeAcknowledge(ackCtxt: JmsAckContext): Unit = {
      log.info(s"Acknowledged envelope [${ackCtxt.envelope.id}] for session [${ackCtxt.session.sessionId}]")
      ackCtxt.jmsMessage.acknowledge()
    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new JmsSourceLogic()
}
