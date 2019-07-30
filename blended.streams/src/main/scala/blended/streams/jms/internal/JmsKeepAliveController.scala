package blended.streams.jms.internal

import akka.NotUsed
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils._
import blended.streams.{AbstractStreamController, StreamController, StreamControllerConfig}
import blended.streams.jms.{AcknowledgeMode, JMSConsumerSettings, JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.{LogLevel, Logger}

import scala.concurrent.duration._

object JmsKeepAliveController{
  def props(idSvc : ContainerIdentifierService) : Props =
    Props(new JmsKeepAliveController(idSvc))
}

/**
 * Keep track of connection factories registered as services andd create KeepAlive Streams as appropriate.
 */
class JmsKeepAliveController(idSvc : ContainerIdentifierService) extends Actor {

  private val log : Logger = Logger[JmsKeepAliveController]

  override def preStart(): Unit =
    context.become(running(Map.empty))


  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(){
    case _ => Stop
  }

  private val cfKey : IdAwareConnectionFactory => String = cf => s"${cf.vendor}${cf.provider}"

  override def receive: Receive = Actor.emptyBehavior

  private def running(watched : Map[String, ActorRef]) : Receive = {
    case AddedConnectionFactory(cf) =>
      if (!watched.contains(cfKey(cf))) {
        log.info(s"Starting keep Alive actor for JMS connection factory [${cf.vendor}:${cf.provider}]")
        val actor : ActorRef = context.system.actorOf(JmsKeepAliveActor.props(idSvc, cf))
        context.watch(actor)
        context.become(running(watched + (cfKey(cf) -> actor)))
      }

    case RemovedConnectionFactory(cf) => watched.get(cfKey(cf)).foreach { actor =>
      log.info(s"Stopping keep Alive Actor for JMS connection factory [${cf.vendor}:${cf.provider}]")
      context.system.stop(actor)
      context.become(running(watched - cfKey(cf)))
    }

    case Terminated(a) =>
      watched.find{ case (_, v) => v == a }.foreach{ case (k,_) =>
        log.warn(s"Jms KeepAliveActor [$k] terminated ...")
        context.become(running(watched - k))
      }
  }
}

object JmsKeepAliveActor {

  def props(idSvc : ContainerIdentifierService, cf : IdAwareConnectionFactory) : Props =
    Props(new JmsKeepAliveActor(idSvc, cf))
}

trait KeepAliveProducer {
  val createProducer : BlendedSingleConnectionFactory => ActorRef
}

class JmsKeepAliveActor(idSvc : ContainerIdentifierService, cf : IdAwareConnectionFactory) extends Actor
  with JmsStreamSupport {

  private val log : Logger = Logger[JmsKeepAliveActor]
  private implicit val materializer : Materializer = ActorMaterializer()

  private val producerSettings : BlendedSingleConnectionFactory => JmsProducerSettings = bcf =>
    JmsProducerSettings(
      log = log,
      headerCfg = FlowHeaderConfig.create(idSvc),
      connectionFactory = bcf,
      jmsDestination = Some(JmsDestination.create(bcf.config.pingDestination).get),
      timeToLive = Some(bcf.config.keepAliveInterval)
    )

  private val consumerSettings : BlendedSingleConnectionFactory => JMSConsumerSettings = bcf =>
    JMSConsumerSettings(
      log = log,
      headerCfg = FlowHeaderConfig.create(idSvc),
      connectionFactory = bcf,
      jmsDestination = Some(JmsDestination.create(bcf.config.pingDestination).get),
      receiveLogLevel = LogLevel.Debug,
      acknowledgeMode = AcknowledgeMode.AutoAcknowledge,
      selector = Some(s"JMSCorrelationID = '${idSvc.uuid}'")
    )

  private val createProducer : BlendedSingleConnectionFactory => ActorRef = { bcf =>

    val producer : Sink[FlowEnvelope, NotUsed] = jmsProducer(
      name = s"KeepAlive-send-${bcf.vendor}-${bcf.provider}",
      settings = producerSettings(bcf),
      autoAck = true
    ).to(Sink.ignore)

    val consumer : Source[FlowEnvelope, NotUsed] = jmsConsumer(
      name = s"KeepAlive-Rec-${cf.vendor}-${cf.provider}",
      settings = consumerSettings(bcf),
      minMessageDelay = None
    )

    val keepAliveSource : Source[FlowEnvelope, ActorRef] = Source.actorRef(
      10, OverflowStrategy.dropBuffer
    ).viaMat(Flow.fromSinkAndSourceCoupled(producer, consumer))(Keep.left)

    val streamCfg : StreamControllerConfig = StreamControllerConfig(
      name = s"KeepAlive-stream-${bcf.vendor}-${bcf.provider}",
      minDelay = 1.second,
      maxDelay = 1.minute,
      exponential = true,
      onFailureOnly = true,
      random = 0.2
    )

    val controller : Props =
      StreamController.props[FlowEnvelope, ActorRef](keepAliveSource, streamCfg)(onMaterialize = { actor =>
        log.info(s"Producer actor is [${actor}]")
      })

    context.system.actorOf(controller)
  }

  override def preStart(): Unit = {
    cf match {
      case bcf : BlendedSingleConnectionFactory =>
        if (bcf.config.keepAliveEnabled) {
          context.become(running(bcf))
        } else {
          context.stop(self)
        }

      case _ =>
        log.info(s"No keep alive configuration found for [${cf.vendor}:${cf.provider}]")
        context.stop(self)
    }
  }

  override def receive: Receive = Actor.emptyBehavior

  private def running(bcf : BlendedSingleConnectionFactory) : Receive = Actor.emptyBehavior
}
