package blended.streams.jms

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.stage.{AsyncCallback, GraphStage, GraphStageLogic}
import blended.jms.utils._
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope, FlowEnvelopeLogger, FlowMessage}
import blended.streams.{AckSourceLogic, DefaultAcknowledgeContext, FlowHeaderConfig}
import blended.util.RichTry._
import blended.util.logging.LogLevel
import javax.jms.{Message, MessageConsumer}

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
 * or acknowledged. The messages that are waiting for acknowledgement are maintained
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
 * to check whether a message can be passed downstream already.
 */
final class JmsConsumerStage(
  name: String,
  consumerSettings: JmsConsumerSettings,
  minMessageDelay: Option[FiniteDuration] = None
)(implicit actorSystem: ActorSystem)
    extends GraphStage[SourceShape[FlowEnvelope]] {

  consumerSettings.log.underlying.debug(s"Starting consumer [$name] with ackTimeout [${consumerSettings.ackTimeout}]")

  private val headerConfig: FlowHeaderConfig = consumerSettings.headerCfg
  private val out: Outlet[FlowEnvelope] = Outlet[FlowEnvelope](s"JmsAckSource($name.out)")
  override val shape: SourceShape[FlowEnvelope] = SourceShape[FlowEnvelope](out)

  private class JmsAckContext(
    inflightId: String,
    env: FlowEnvelope,
    val jmsMessage: Message,
    val jmsMessageAck: Message => Unit,
    val session: JmsSession,
    val sessionClose: JmsSession => Unit,
    val sessionRecover: JmsSession => Unit
  ) extends DefaultAcknowledgeContext(inflightId, env, System.currentTimeMillis()) {

    override def deny(): Unit = {
      sessionRecover(session)
      consumerSettings.log.logEnv(
        env,
        LogLevel.Debug,
        s"Message [${envelope.id}] has been denied. Recovering receiving session."
      )
    }

    override def acknowledge(): Unit = {
      jmsMessageAck(jmsMessage)
      consumerSettings.log.logEnv(
        env,
        LogLevel.Debug,
        s"Acknowledged envelope [${envelope.id}] for session [${session.sessionId}]"
      )
    }
  }

  private class JmsSourceLogic()
      extends AckSourceLogic[JmsAckContext](shape, out, consumerSettings.ackTimeout)
      with JmsEnvelopeHeader {

    /** The id to identify the instance in the log files */
    override protected val id: String = name

    override val log: FlowEnvelopeLogger = consumerSettings.log
    override val autoAcknowledge: Boolean = consumerSettings.acknowledgeMode == AcknowledgeMode.AutoAcknowledge

    private var stateListener: Option[ActorRef] = None

    private val handleError: AsyncCallback[Throwable] = getAsyncCallback[Throwable](t => failStage(t))

    private val srcDest: JmsDestination = consumerSettings.jmsDestination match {
      case Some(d) => d
      case None    => throw new IllegalArgumentException(s"Destination must be set for consumer in [$id]")
    }

    private val vendor: String = consumerSettings.connectionFactory.vendor
    private val provider: String = consumerSettings.connectionFactory.provider

    private[this] val consumer: mutable.Map[String, MessageConsumer] = mutable.Map.empty

    private[this] def addConsumer(s: String, c: MessageConsumer): Unit = {
      consumer.put(s, c)
      consumerSettings.log.underlying.debug(s"Jms Consumer count of [$name] is [${consumer.size}]")
      nextPollRelative = None
      pollImmediately.invoke(())
    }

    private[this] def removeConsumer(s: String): Unit = {
      consumer.remove(s)
      consumerSettings.log.underlying.debug(s"Consumer count of [$name] is [${consumer.size}]")
    }

    private val recoverSession: AsyncCallback[JmsSession] = getAsyncCallback(s => s.recoverSession())
    private val closeSession: AsyncCallback[JmsSession] = getAsyncCallback(s => connector.closeSession(s.sessionId))
    private val ackMessage: AsyncCallback[Message] = getAsyncCallback[Message](m => m.acknowledge())

    private lazy val connector: JmsConnector = new JmsConnector(id, consumerSettings)(session =>
      Try {
        // After session opened
        consumerSettings.log.underlying.debug(
          s"Creating message consumer for session [${session.sessionId}], " +
            s"destination [$srcDest] and selector [${consumerSettings.selector}]"
        )
        session.createConsumer(srcDest, consumerSettings.selector) match {

          case Success(c) =>
            addConsumer(session.sessionId, c)

          case Failure(e) =>
            consumerSettings.log.underlying
              .debug(s"Failed to create consumer for session [${session.sessionId}] : [${e.getMessage()}]")
            closeSession.invoke(session)
        }
      }
    )(s =>
      Try {
        // before session close
        consumer.get(s.sessionId).foreach { c =>
          consumerSettings.log.underlying.debug(s"Closing message consumer for [${s.sessionId}]")
          c.close()
          removeConsumer(s.sessionId)
        }
      }
    )(
      //after Session close
      _ => Success(())
    )(
      // error handler
      handleError.invoke
    )

    override protected def freeInflightSlot(): Option[String] =
      determineNextSlot(inflightSlots.filter(id => connector.isOpen(id))) match {
        case Some(s) => Some(s)
        case None    => determineNextSlot(inflightSlots)
      }

    /** The id's of the available inflight slots */
    override protected val inflightSlots: List[String] =
      1.to(consumerSettings.sessionCount).map(i => s"$id-$i").toList

    private var nextPollRelative: Option[FiniteDuration] = None
    override protected def nextPoll(): Option[FiniteDuration] = {
      nextPollRelative match {
        case None =>
          Some(consumerSettings.pollInterval)
        case Some(npr) =>
          nextPollRelative = None
          consumerSettings.log.underlying.debug(s"Overriding next poll interval with [$npr]")
          Some(npr)
      }
    }

    private def receive(session: JmsSession): Try[Option[Message]] =
      Try {

        val msg: Option[Message] = consumer.get(session.sessionId).flatMap { c =>
          if (consumerSettings.receiveTimeout.toMillis <= 0) {
            Option(c.receiveNoWait())
          } else {
            Option(c.receive(consumerSettings.receiveTimeout.toMillis))
          }
        }

        val result: Option[Message] = msg match {
          case None => None

          case Some(m) =>
            minMessageDelay match {
              case Some(d) =>
                val age: Long = System.currentTimeMillis() - m.getJMSTimestamp()
                if (age <= d.toMillis) {
                  closeSession.invoke(session)
                  nextPollRelative = Some((d.toMillis - age).millis)
                  consumerSettings.log.underlying.debug(
                    s"Message has not reached the minimum message delay yet ...rescheduling in [$nextPollRelative]"
                  )
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

    private def createEnvelope(message: Message, ackHandler: AcknowledgeHandler): Try[FlowEnvelope] =
      Try {

        val flowMessage: FlowMessage = JmsFlowSupport.jms2flowMessage(headerConfig)(consumerSettings)(message).unwrap

        val envelopeId: String = flowMessage.header[String](headerConfig.headerTransId) match {
          case None =>
            val newId = UUID.randomUUID().toString()
            consumerSettings.log.underlying.trace(s"Created new envelope id [$newId]")
            newId
          case Some(s) =>
            consumerSettings.log.underlying.trace(s"Reusing transaction id [$s] as envelope id")
            s
        }

        FlowEnvelope(flowMessage, envelopeId)
          .withHeader(headerConfig.headerTransId, envelopeId)
          .unwrap
          .withRequiresAcknowledge(true)
          .withAckHandler(Some(ackHandler))
      }

    override protected def doPerformPoll(id: String, ackHandler: AcknowledgeHandler): Try[Option[JmsAckContext]] =
      Try {
        connector.getSession(id) match {
          case Some(s) =>
            receive(s).unwrap
              .map { m =>
                val e: FlowEnvelope = createEnvelope(m, ackHandler).unwrap

                val now: Long = System.currentTimeMillis()

                val msgAge: Long = now - e.header[Long](timestampHeader(headerConfig.prefix)).getOrElse(now)

                consumerSettings.log.logEnv(
                  e,
                  consumerSettings.logLevel(e),
                  s"Message received [${e.id}][${consumerSettings.jmsDestination.map(_.asString)}] after " +
                    s"[$msgAge]ms in [${s.sessionId}] : ${e.flowMessage}"
                )

                // We signal that we have received a message for the underlying connection factory
                actorSystem.eventStream.publish(MessageReceived(vendor, provider, e.id))

                new JmsAckContext(
                  inflightId = id,
                  env = e,
                  jmsMessage = m,
                  jmsMessageAck = ackMessage.invoke,
                  session = s,
                  sessionClose = closeSession.invoke,
                  sessionRecover = recoverSession.invoke
                )
              }

          case None =>
            None
        }
      }

    override def preStart(): Unit = {
      super.preStart()

      stateListener = Some(
        ConnectionStateListener.create(
          vendor = consumerSettings.connectionFactory.vendor,
          provider = consumerSettings.connectionFactory.provider
        ) { event =>
          event.state.status match {
            case Disconnected =>
              val msg: String = s"Underlying JMS connection closed for [$id]"
              consumerSettings.log.underlying.debug(msg)
              val t: Throwable = new Exception(msg)
              handleError.invoke(t)
            case _ =>
          }
        }
      )
    }

    override def postStop(): Unit = {
      log.underlying.debug(s"Stopping JmsConsumerStage [$id].")
      stateListener.foreach(actorSystem.stop)
      connector.closeAll()
      super.postStop()
    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new JmsSourceLogic()
}
