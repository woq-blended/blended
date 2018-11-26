package blended.streams.jms

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage._
import blended.jms.utils.JmsProducerSession
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger
import javax.jms.{Connection, MessageProducer}
import scala.concurrent.duration._

import scala.util.Random

class JmsSinkStage(
  name: String, settings : JmsProducerSettings, log : Logger
)(implicit actorSystem : ActorSystem)
  extends GraphStage[FlowShape[FlowEnvelope, FlowEnvelope]] {

  private case class Push(env: FlowEnvelope)

  private val in = Inlet[FlowEnvelope](s"JmsSink($name.in)")
  private val out = Outlet[FlowEnvelope](s"JmsSink($name.out)")

  override val shape : FlowShape[FlowEnvelope, FlowEnvelope] = FlowShape(in, out)

  override protected def initialAttributes: Attributes =
    ActorAttributes.dispatcher("FixedPool")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new JmsStageLogic[JmsProducerSession, JmsProducerSettings](
      settings,
      inheritedAttributes,
      shape,
      log
    ) with JmsConnector[JmsProducerSession] {

      override private[jms] val handleError = getAsyncCallback[Throwable]{ ex =>
       failStage(ex)
      }

      private[this] val rnd = new Random()
      private[this] var producer : Option[MessageProducer] = None

      override protected def onTimer(timerKey: Any): Unit = {
        timerKey match {
          case Push(env) => pushMessage(env)
        }
      }

      private def pushMessage(env: FlowEnvelope) : Unit = {
        if (jmsSessions.size > 0) {
          push(out, sendMessage(env))
        } else {
          scheduleOnce(Push(env), 10.millis)
        }
      }

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
        log.trace(s"Created anonymous producer for [${jmsSession.sessionId}]")
      }

      def sendMessage(env: FlowEnvelope): FlowEnvelope = {
        log.trace(s"Trying to send envelope [$env]")
        // select one sender session randomly
        val idx : Int = rnd.nextInt(jmsSessions.size)
        val key = jmsSessions.keys.takeRight(idx+1).head
        val p = jmsSessions.toIndexedSeq(idx)
        val session = p._2

        val outEnvelope : FlowEnvelope = try {
          val sendParams = JmsFlowSupport.envelope2jms(jmsSettings, session.session, env).get
          producer.foreach { p =>
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
              log.debug(s"Successfuly sent message to [$dest] with headers [${env.flowMessage.header.mkString(",")}] with parameters [${sendParams.destination}, ${sendParams.deliveryMode}, ${sendParams.priority}, ${sendParams.ttl}]@[$id]")
            }
          }
          env
        } catch {
          case t : Throwable =>
            log.error(t)(s"Error sending message to [${jmsSettings.jmsDestination}] JMS [$env] in [${session.sessionId}]")
            env.withException(t)
        }

        outEnvelope
      }

      // First simply pass the pull upstream if any
      setHandler(out,
        new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        }
      )

      // We can only start pushing message after at least one session is available
      setHandler(in,
        new InHandler {

          override def onPush(): Unit = {

            val env = grab(in)
            pushMessage(env)
          }
        }
      )
    }
}
