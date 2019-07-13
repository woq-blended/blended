package blended.streams.jms

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage._
import blended.jms.utils.{JmsDestination, JmsSession}
import blended.streams.message.FlowEnvelope
import blended.util.RichTry._
import javax.jms.{Destination, MessageProducer}

import scala.concurrent.duration._
import scala.util.{Failure, Random, Success, Try}

class JmsSinkStage(
  name : String,
  producerSettings: JmsProducerSettings
)(implicit actorSystem : ActorSystem)
  extends GraphStage[FlowShape[FlowEnvelope, FlowEnvelope]] {

  private case class Push(env : FlowEnvelope)

  private val inlet : Inlet[FlowEnvelope] = Inlet[FlowEnvelope](s"JmsSink($name.in)")
  private val outlet : Outlet[FlowEnvelope] = Outlet[FlowEnvelope](s"JmsSink($name.out)")

  override val shape : FlowShape[FlowEnvelope, FlowEnvelope] = FlowShape(inlet, outlet)

  private class JmsSinkLogic extends TimerGraphStageLogic(shape) with JmsConnector {

    override protected def system: ActorSystem = actorSystem
    override protected def jmsSettings: JmsSettings = producerSettings

    private[this] val rnd = new Random()
    private[this] var producer : Map[String, MessageProducer] = Map.empty

    private[this] def addProducer(s : String, p : MessageProducer) : Unit = {
      producer = producer + (s -> p)
      producerSettings.log.debug(s"Producer count of [$id] is [${producer.size}]")
    }

    private[this] def removeProducer(s : String) : Unit = {
      if (producer.contains(s)) {
        producer = producer.filterKeys(_ != s)
        producerSettings.log.debug(s"Producer count of [$id] is [${producer.size}]")
      }
    }

    override protected def beforeSessionCloseCallback: JmsSession => Try[Unit] = { s => Try {
      producer.get(s.sessionId).foreach{ p =>
        producerSettings.log.debug(s"Closing producer for session [${s.sessionId}]")
        p.close()
        removeProducer(s.sessionId)
      }
    }}

    override protected def onSessionOpened: JmsSession => Try[Unit] = s => Try {
      // scalastyle:off null
      val p : MessageProducer = s.session.createProducer(null)
      // scalastyle:on null
      addProducer(s.sessionId, p)
    }

    private def pushMessage(env : FlowEnvelope) : Unit = {

      // We don't have any producers yet, so we will reschedule the message send
      if (producer.isEmpty) {
        scheduleOnce(Push(env), 10.millis)
      } else {
        // We select a producer and the corresponding session randomly
        val idx: Int = rnd.nextInt(producer.size)
        val (key, jmsProd): (String, MessageProducer) = producer.toIndexedSeq(idx)
        sessionMgr.getSession(key) match {
          case Success(None) =>
            val t : Throwable = new IllegalStateException(s"No session available though consumer exists in [$id].")
            handleError.invoke(t)
            throw t
          case Success(Some(sess)) =>
            push(outlet, sendEnvelope(env)(sess)(jmsProd))

          case Failure(t) =>
            handleError.invoke(t)
            throw t
        }
      }
    }

    private val sendEnvelope : FlowEnvelope => JmsSession => MessageProducer => FlowEnvelope = env => jmsSession => p => {

      try {
        val sendParams = JmsFlowSupport.envelope2jms(producerSettings, jmsSession.session, env).unwrap

        def sendMessage(env: FlowEnvelope): FlowEnvelope = {

          var jmsDest: Option[JmsDestination] = None

          producerSettings.log.debug(s"Trying to send envelope [${env.id}][${env.flowMessage.header.mkString(",")}]")
          // select one sender session randomly

          val outEnvelope: FlowEnvelope = try {

            val sendParams = JmsFlowSupport.envelope2jms(producerSettings, jmsSession.session, env).unwrap

            val sendTtl: Long = sendParams.ttl match {
              case Some(l) => if (l.toMillis < 0L) {
                producerSettings.log.warn(s"The message [${env.id}] has expired and wont be sent to the JMS destination.")
              }
                l.toMillis
              case None => 0L
            }

            if (sendTtl >= 0L) {
              jmsDest = Some(sendParams.destination)
              val dest: Destination = sendParams.destination.create(jmsSession.session)
              p.send(dest, sendParams.message, sendParams.deliveryMode.mode, sendParams.priority, sendTtl)
              val logDest = s"${producerSettings.connectionFactory.vendor}:${producerSettings.connectionFactory.provider}:$dest"
              producerSettings.log.debug(
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
            case t: Throwable =>
              producerSettings.log.error(t)(s"Error sending message [${env.id}] to [$jmsDest] in [${jmsSession.sessionId}]")
              sessionMgr.closeSession(jmsSession.sessionId)
              env.withException(t)
          }

          outEnvelope
        }
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
  }

  override def createLogic(inheritedAttributes : Attributes) : GraphStageLogic = new JmsSinkLogic()
}
