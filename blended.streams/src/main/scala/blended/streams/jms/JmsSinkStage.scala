package blended.streams.jms

import java.util.concurrent.Semaphore

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage._
import blended.jms.utils.JmsProducerSession
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger
import javax.jms.Connection

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

      private[this] val rnd = new Random()

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
          env
        } catch {
          case t : Throwable =>
            log.error(s"Error sending message to JMS [] in [${session.sessionId}]")
            env.withException(t)
        }

        push(out, outEnvelope)
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
