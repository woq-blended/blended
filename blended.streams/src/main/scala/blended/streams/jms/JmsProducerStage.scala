package blended.streams.jms

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage._
import blended.jms.utils.JmsSession
import blended.streams.message.FlowEnvelope
import blended.util.RichTry._
import javax.jms.{Destination, MessageProducer}

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class JmsProducerStage(
  name : String,
  producerSettings: JmsProducerSettings
)(implicit actorSystem : ActorSystem)
  extends GraphStage[FlowShape[FlowEnvelope, FlowEnvelope]] {

  producerSettings.log.info(s"Starting producer [$name]")

  private case class Push(env : FlowEnvelope)

  private val inlet : Inlet[FlowEnvelope] = Inlet[FlowEnvelope](s"JmsProducer($name.in)")
  private val outlet : Outlet[FlowEnvelope] = Outlet[FlowEnvelope](s"JmsProducer($name.out)")

  override val shape : FlowShape[FlowEnvelope, FlowEnvelope] = FlowShape(inlet, outlet)

  private class JmsSinkLogic extends TimerGraphStageLogic(shape) {

    // Just to identify the Source stage in log statements
    private val id : String = name

    private val handleError : AsyncCallback[Throwable] = getAsyncCallback[Throwable]{ t =>
      producerSettings.log.error(t)(s"Error in stage [$name] : [${t.getMessage()}]")
      failStage(t)
    }

    private var producer : Option[MessageProducer] = None

    val connector : JmsConnector = new JmsConnector(id, producerSettings)(s => Try {
      // scalastyle:off null
      producer = Some(s.session.createProducer(null))
      // scalastyle:on null
    })(_ =>  Try {
      producer = None
    })(t => failStage(t))

    private def pushMessage(env : FlowEnvelope) : Unit = {

      connector.sessionMgr.getSession(id) match {
        case Success(Some(s)) =>
          producer match {
            case None =>
              producerSettings.log.debug(s"No producer available for [$id]")
              scheduleOnce(Push(env), 10.millis)

            case Some(p) =>
              push(outlet, sendEnvelope(env)(s)(p))
          }

        case Success(None) =>
          producerSettings.log.warn(s"No producer session available in [$id]")
          scheduleOnce(Push(env), 10.millis)

        case Failure(t) =>
          handleError.invoke(t)
      }
    }

    private val sendEnvelope : FlowEnvelope => JmsSession => MessageProducer => FlowEnvelope = env => jmsSession => producer => {

      try {
        val sendParams = JmsFlowSupport.envelope2jms(producerSettings, jmsSession.session, env).unwrap

        val sendTtl : Long = sendParams.ttl match {
          case Some(l) =>
            if (l.toMillis < 0L) {
              producerSettings.log.warn(
                s"The message [${env.id}] has expired and wont be sent to the JMS destination."
              )
            }
            l.toMillis
          case None => 0L
        }

        if (sendTtl >= 0L) {
          val dest : Destination = sendParams.destination.create(jmsSession.session)
          producer.send(
            dest, sendParams.message, sendParams.deliveryMode.mode, sendParams.priority, sendTtl
          )

          val logDest = s"${producerSettings.connectionFactory.vendor}:${producerSettings.connectionFactory.provider}:$dest"
          producerSettings.log.info(
            s"Successfully sent message to [$logDest] with headers [${env.flowMessage.header.mkString(",")}] " +
              s"with parameters [${sendParams.deliveryMode}, ${sendParams.priority}, ${sendParams.ttl}]"
          )
        }

        if (producerSettings.clearPreviousException) {
          env.clearException()
        } else {
          env
        }
      } catch {
        case NonFatal(t) =>
          producerSettings.log.error(t)(s"Error sending message [${env.id}] to [${producerSettings.jmsDestination}] in [${jmsSession.sessionId}]")
          connector.sessionMgr.closeSession(jmsSession.sessionId)
          env.withException(t)
      }
    }

    // First simply pass the pull upstream if any
    setHandler(
      outlet,
      new OutHandler {
        override def onPull() : Unit = {
          pull(inlet)
        }
      }
    )

    // We can only start pushing message after at least one session is available
    setHandler(
      inlet,
      new InHandler {
        override def onPush() : Unit = {
          val env = grab(inlet)
          pushMessage(env)
        }
      }
    )

    override protected def onTimer(timerKey: Any): Unit = timerKey match {
      case Push(env) => pushMessage(env)
    }

    override def postStop(): Unit = {
      connector.sessionMgr.closeAll()
    }
  }

  override def createLogic(inheritedAttributes : Attributes) : GraphStageLogic = new JmsSinkLogic()
}
