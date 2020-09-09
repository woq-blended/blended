package blended.streams.jms

import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.stage._
import blended.jms.utils.{ConnectionStateListener, Disconnected, JmsSession}
import blended.streams.message.FlowEnvelope
import blended.util.RichTry._
import blended.util.logging.LogLevel
import javax.jms.{Destination, MessageProducer}

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Random, Success, Try}

final class JmsProducerStage(
  name : String,
  producerSettings: JmsProducerSettings
)(implicit system : ActorSystem) extends GraphStage[FlowShape[FlowEnvelope, FlowEnvelope]] {

  producerSettings.log.underlying.debug(s"Starting producer [$name]")

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

    private var stateListener : Option[ActorRef] = None
    private var producer : Map[String, MessageProducer] = Map.empty

    private[this] def addProducer(s : String, p : MessageProducer) : Unit = {
      producer = producer + (s -> p)
      producerSettings.log.underlying.debug(s"Producer count of [$id] is [${producer.size}]")
    }

    private[this] def removeProducer(s : String) : Unit = {
      if (producer.contains(s)) {
        producer = producer.view.filterKeys(_ != s).toMap
        producerSettings.log.underlying.debug(s"Producer count of [$id] is [${producer.size}]")
      }
    }

    private val connector : JmsConnector = new JmsConnector(id, producerSettings)(s => Try {
      // scalastyle:off null
      val p : MessageProducer = s.session.createProducer(null)
      // scalastyle:on null
      addProducer(s.sessionId, p)
    })( s => Try {
      producer.get(s.sessionId).foreach{ p =>
        producerSettings.log.underlying.debug(s"Closing message producer for [${s.sessionId}]")
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
            producerSettings.log.underlying.debug(s"No producer session available in [$id]")
            scheduleOnce(Push(env), 10.millis)
        }
      } else {
        // Kick off to initialize the producers
        0.to(producerSettings.sessionCount).foreach{ i =>
          connector.getSession(name + "-" + i)
        }

        producerSettings.log.underlying.debug(s"No producer available")
        scheduleOnce(Push(env), 10.millis)
      }
    }

    private val sendEnvelope : FlowEnvelope => JmsSession => MessageProducer => FlowEnvelope = env => jmsSession => producer => {

      try {
        val sendParams = JmsFlowSupport.envelope2jms(producerSettings, jmsSession.session, env).unwrap

        val sendTtl : Long = sendParams.ttl match {
          case Some(l) =>
            if (l.toMillis < 0L) {
              producerSettings.log.logEnv(env, LogLevel.Warn,
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
          producerSettings.log.logEnv(env, producerSettings.logLevel(env),
            s"Successfully sent message [${env.id}] to [$logDest] with headers [${env.flowMessage.header.mkString(",")}] " +
              s"with parameters [${sendParams.deliveryMode}, ${sendParams.priority}, ${sendParams.ttl}]", withStacktrace = false
          )
        }

        if (producerSettings.clearPreviousException) {
          env.clearException()
        } else {
          env
        }
      } catch {
        case NonFatal(t) =>
          producerSettings.log.logEnv(env, LogLevel.Debug,
            s"Error sending message [${env.id}] to [${producerSettings.jmsDestination}] in [${jmsSession.sessionId}]"
          )
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

    override def preStart(): Unit = {
      super.preStart()

      stateListener = Some(ConnectionStateListener.create(
        vendor = producerSettings.connectionFactory.vendor,
        provider = producerSettings.connectionFactory.provider
      ){ event => event.state.status match {
        case Disconnected =>
          val msg : String = s"Underlying JMS connection closed for [$id]"
          producerSettings.log.underlying.warn(msg)
          val t : Throwable = new Exception(msg)
          handleError.invoke(t)
        case _ =>
      }})
    }

    override def postStop(): Unit = {
      producerSettings.log.underlying.debug(s"Closing JMS Producer stage [$id]")
      stateListener.foreach(system.stop)
      connector.closeAll()
      super.postStop()
    }
  }

  override def createLogic(inheritedAttributes : Attributes) : GraphStageLogic = new JmsSinkLogic()
}
