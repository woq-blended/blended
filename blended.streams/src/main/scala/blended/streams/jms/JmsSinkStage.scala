package blended.streams.jms

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage._
import blended.jms.utils.{JmsDestination, JmsProducerSession}
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger
import javax.jms.{Connection, Destination, MessageProducer}

import scala.concurrent.duration._
import scala.util.Random

class JmsSinkStage(
  name: String, settings : JmsProducerSettings
)(implicit actorSystem : ActorSystem)
  extends GraphStage[FlowShape[FlowEnvelope, FlowEnvelope]] {

  private case class Push(env: FlowEnvelope)

  private val in = Inlet[FlowEnvelope](s"JmsSink($name.in)")
  private val out = Outlet[FlowEnvelope](s"JmsSink($name.out)")

  override val shape : FlowShape[FlowEnvelope, FlowEnvelope] = FlowShape(in, out)

//  override protected def initialAttributes: Attributes =
//    ActorAttributes.dispatcher("FixedPool")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new JmsStageLogic[JmsProducerSession, JmsProducerSettings](
      settings,
      inheritedAttributes,
      shape,
    ) with JmsConnector[JmsProducerSession] {

      override private[jms] val handleError = getAsyncCallback[Throwable]{ ex =>
        settings.log.warn(s"Failing stage [$name]")
        failStage(ex)
      }

      private[this] val rnd = new Random()
      private[this] var producer : Option[MessageProducer] = None

      override protected def handleTimer: PartialFunction[Any, Unit] = super.handleTimer orElse {
        case Push(env) => pushMessage(env)
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

        val result : JmsProducerSession = JmsProducerSession(
          connection = connection,
          session = session,
          sessionId = nextSessionId(),
          jmsDestination = jmsSettings.jmsDestination
        )

        settings.log.debug(s"Producer session [${result.sessionId}] has been created")

        result
      }

      override protected def onSessionOpened(jmsSession: JmsProducerSession): Unit = {
        producer = Some(jmsSession.session.createProducer(null))
        settings.log.debug(s"Created anonymous producer for [${jmsSession.sessionId}]")
      }

      def sendMessage(env: FlowEnvelope): FlowEnvelope = {

        var jmsDest : Option[JmsDestination] = None

        settings.log.debug(s"Trying to send envelope [${env.id}][${env.flowMessage.header.mkString(",")}]")
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
                  settings.log.warn(s"The message [${env.id}] has expired and wont be sent to the JMS destination.")
                }
                l.toMillis
              case None => 0L
            }
            if (sendTtl >= 0L) {
              jmsDest = Some(sendParams.destination)
              val dest : Destination = sendParams.destination.create(session.session)
              p.send(dest, sendParams.message, sendParams.deliveryMode.mode, sendParams.priority, sendTtl)
              val logDest = s"${settings.connectionFactory.vendor}:${settings.connectionFactory.provider}:$dest"
              settings.log.debug(s"Successfuly sent message to [$logDest] with headers [${env.flowMessage.header.mkString(",")}] with parameters [${sendParams.deliveryMode}, ${sendParams.priority}, ${sendParams.ttl}]")
            }
          }

          if (settings.clearPreviousException) {
            env.clearException()
          } else {
            env
          }
        } catch {
          case t : Throwable =>
            settings.log.error(t)(s"Error sending message [${env.id}] to [$jmsDest] in [${session.sessionId}]")
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
