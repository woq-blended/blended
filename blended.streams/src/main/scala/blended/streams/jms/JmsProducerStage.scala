package blended.streams.jms

import akka.stream._
import akka.stream.stage._
import blended.jms.utils.JmsSession
import blended.streams.message.FlowEnvelope
import blended.util.RichTry._
import javax.jms.{Destination, MessageProducer}

import scala.concurrent.duration._
import scala.util.{Random, Success, Try}
import scala.util.control.NonFatal

class JmsProducerStage(
  name : String,
  producerSettings: JmsProducerSettings
) extends GraphStage[FlowShape[FlowEnvelope, FlowEnvelope]] {

  producerSettings.log.debug(s"Starting producer [$name]")

  private case class Push(env : FlowEnvelope)

  private val inlet : Inlet[FlowEnvelope] = Inlet[FlowEnvelope](s"JmsProducer($name.in)")
  private val outlet : Outlet[FlowEnvelope] = Outlet[FlowEnvelope](s"JmsProducer($name.out)")

  override val shape : FlowShape[FlowEnvelope, FlowEnvelope] = FlowShape(inlet, outlet)

  private class JmsSinkLogic extends TimerGraphStageLogic(shape) {

    // Just to identify the Source stage in log statements
    private val id : String = name
    private val rnd : Random = new Random()

    private val handleError : AsyncCallback[Throwable] = getAsyncCallback[Throwable]{ t =>
      failStage(t)
    }

    private var producer : Map[String, MessageProducer] = Map.empty

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

    private val connector : JmsConnector = new JmsConnector(id, producerSettings)(s => Try {
      // scalastyle:off null
      val p : MessageProducer = s.session.createProducer(null)
      // scalastyle:on null
      addProducer(s.sessionId, p)
    })( s => Try {
      producer.get(s.sessionId).foreach{ p =>
        producerSettings.log.debug(s"Closing message producer for [${s.sessionId}]")
        p.close()
        removeProducer(s.sessionId)
      }
    }
    )(_ =>  Success(()))(
      handleError.invoke
    )

    private val closeSession : AsyncCallback[JmsSession] = getAsyncCallback(s => connector.closeSession(s.sessionId))

    private def pushMessage(env : FlowEnvelope) : Unit = {
      if (producer.nonEmpty) {
        val idx: Int = rnd.nextInt(producer.size)
        val (key, jmsProd): (String, MessageProducer) = producer.toIndexedSeq(idx)

        connector.getSession(key) match {
          case Some(s) =>
            push(outlet, sendEnvelope(env)(s)(jmsProd))

          case None =>
            producerSettings.log.warn(s"No producer session available in [$id]")
            scheduleOnce(Push(env), 10.millis)
        }
      } else {
        // Kick off to initialize the producers
        0.to(producerSettings.sessionCount).foreach{ i =>
          connector.getSession(name + "-" + i)
        }

        producerSettings.log.debug(s"No producer available")
        scheduleOnce(Push(env), 10.millis)
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
        case NonFatal(t) =>
          producerSettings.log.error(t)(s"Error sending message [${env.id}] to [${producerSettings.jmsDestination}] in [${jmsSession.sessionId}]")
          closeSession.invoke(jmsSession)
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
      producerSettings.log.debug(s"Closing JMS Producer stage [$id]")
      connector.closeAll()
    }
  }

  override def createLogic(inheritedAttributes : Attributes) : GraphStageLogic = new JmsSinkLogic()
}
