package blended.streams.jms

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage._
import blended.jms.utils.JmsProducerSession
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger
import javax.jms.{Connection, MessageProducer}

import scala.util.Random

class JmsSinkStage(settings : JmsProducerSettings)(implicit actorSystem : ActorSystem)
  extends GraphStage[FlowShape[FlowEnvelope, FlowEnvelope]] {

  private val in = Inlet[FlowEnvelope]("JmsSink.in")
  private val out = Outlet[FlowEnvelope]("JmsSink.out")
  private[this] val log = Logger[JmsSinkStage]

  private var pushEnv: Option[FlowEnvelope] = None

  override val shape : FlowShape[FlowEnvelope, FlowEnvelope] = FlowShape(in, out)

  override protected def initialAttributes: Attributes =
    ActorAttributes.dispatcher("FixedPool")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new JmsStageLogic[JmsProducerSession, JmsProducerSettings](
      settings,
      inheritedAttributes,
      shape
    ) with JmsConnector[JmsProducerSession] {

      override private[jms] val handleError = getAsyncCallback[Throwable]{ ex =>
       failStage(ex)
      }

      override def preStart(): Unit = {
        if (!jmsSettings.sendParamsFromMessage && jmsSettings.jmsDestination.isEmpty) {
          throw new IllegalArgumentException(s"A JMS Destination must be set in [$jmsSettings]if the message headers are not evaluated for send parameters.")
        }
        super.preStart()
      }

      private[this] val rnd = new Random()
      private[this] var producer : Option[MessageProducer] = None

      override protected def createSession(connection: Connection): JmsProducerSession = {
        val session = connection.createSession(false, AcknowledgeMode.AutoAcknowledge.mode)
        new JmsProducerSession(
          connection = connection,
          session = session,
          sessionId = nextSessionId(),
          jmsDestination = jmsSettings.jmsDestination
        )
      }

      override protected def onSessionOpened(jmsSession: JmsProducerSession): Unit = {
        super.onSessionOpened(jmsSession)
        producer = Some(jmsSession.session.createProducer(null))
        pushEnv.foreach(sendMessage)
      }

      def sendMessage(env: FlowEnvelope): Unit = {
        // select one sender session randomly
        val idx : Int = rnd.nextInt(jmsSessions.size)
        val key = jmsSessions.keys.takeRight(idx+1).head
        val p = jmsSessions.toIndexedSeq(idx)
        val session = p._2

        log.debug(s"Using session [${session.sessionId}] to send JMS message.")

        val outEnvelope : FlowEnvelope = try {
          val sendParams = JmsFlowMessage.flowMessage2jms(jmsSettings, session.session, env.flowMessage).get
          producer.foreach { p =>
            log.debug(s"Using JMS send parameter [${sendParams.destination}, ${sendParams.deliveryMode}, ${sendParams.priority}, ${sendParams.ttl}]")

            val sendTtl : Long = sendParams.ttl match {
              case Some(l) => if (l.toMillis < 0L) {
                  log.warn(s"The message [${env.flowMessage}] has expired and wont be sent to the JMS destination.")
                }
                l.toMillis
              case None => 0L
            }
            if (sendTtl >= 0L) {
              val dest = sendParams.destination.create(session.session)
              p.send(dest, sendParams.message, sendParams.deliveryMode.mode, sendParams.priority, sendTtl)
              log.info(s"Successfuly sent [${env.flowMessage}] to [${sendParams.destination}]@[$id]")
            }
          }
          env
        } catch {
          case t : Throwable =>
            log.error(s"Error sending message to JMS [$env] in [${session.sessionId}]")
            env.withException(t)
        }

        push(out, outEnvelope)
        // Todo: Is this pull required ?
        if (!hasBeenPulled(in)) pull(in)
      }

      // First simply pass the pull upstream if any
      setHandler(out,
        new OutHandler {
          override def onPull(): Unit = if (!hasBeenPulled(in)) pull(in)
        }
      )

      setHandler(in,
        new InHandler {

          override def onPush(): Unit = {

            val env = grab(in)

            if (jmsSessions.size > 0) {
              sendMessage(env)
            } else {
              pushEnv = Some(env)
            }
          }
        }
      )
    }
}
