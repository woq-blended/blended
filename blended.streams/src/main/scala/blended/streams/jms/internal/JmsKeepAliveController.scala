package blended.streams.jms.internal

import akka.NotUsed
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils._
import blended.streams.jms.{AcknowledgeMode, JMSConsumerSettings, JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.FlowHeaderConfig
import blended.streams.{StreamController, StreamControllerConfig}
import blended.util.logging.{LogLevel, Logger}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object JmsKeepAliveController{
  def props(idSvc : ContainerIdentifierService, producer : KeepAliveProducerFactory) : Props =
    Props(new JmsKeepAliveController(idSvc, producer))
}

/**
 * Keep track of connection factories registered as services andd create KeepAlive Streams as appropriate.
 */
class JmsKeepAliveController(
  idSvc : ContainerIdentifierService,
  producer : KeepAliveProducerFactory
) extends Actor {

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
        val actor : ActorRef = context.system.actorOf(JmsKeepAliveActor.props(idSvc, cf, producer))
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

trait KeepAliveProducerFactory {
  val createProducer : BlendedSingleConnectionFactory => Future[ActorRef]
  def stop() : Unit = ()
}

class StreamKeepAliveProducerFactory(
  log : BlendedSingleConnectionFactory => Logger,
  idSvc : ContainerIdentifierService
)(implicit system: ActorSystem, materializer : Materializer) extends KeepAliveProducerFactory with JmsStreamSupport {

  private val futMat : Promise[ActorRef] = Promise[ActorRef]
  private var stream : Option[ActorRef] = None

  private val producerSettings : BlendedSingleConnectionFactory => JmsProducerSettings = bcf =>
    JmsProducerSettings(
      log = log(bcf),
      headerCfg = FlowHeaderConfig.create(idSvc),
      connectionFactory = bcf,
      jmsDestination = Some(JmsDestination.create(bcf.config.pingDestination).get),
      timeToLive = Some(bcf.config.keepAliveInterval)
    )

  private val consumerSettings : BlendedSingleConnectionFactory => JMSConsumerSettings = bcf =>
    JMSConsumerSettings(
      log = log(bcf),
      headerCfg = FlowHeaderConfig.create(idSvc),
      connectionFactory = bcf,
      jmsDestination = Some(JmsDestination.create(bcf.config.pingDestination).get),
      receiveLogLevel = LogLevel.Debug,
      acknowledgeMode = AcknowledgeMode.AutoAcknowledge,
      selector = Some(s"JMSCorrelationID = '${idSvc.uuid}'")
    )


  override val createProducer: BlendedSingleConnectionFactory => Future[ActorRef] = { bcf =>

    val producer: Sink[FlowEnvelope, NotUsed] = jmsProducer(
      name = s"KeepAlive-send-${bcf.vendor}-${bcf.provider}",
      settings = producerSettings(bcf),
      autoAck = true
    ).to(Sink.ignore)

    val consumer: Source[FlowEnvelope, NotUsed] = jmsConsumer(
      name = s"KeepAlive-Rec-${bcf.vendor}-${bcf.provider}",
      settings = consumerSettings(bcf),
      minMessageDelay = None
    )

    // scalastyle:off magic.number
    val keepAliveSource: Source[FlowEnvelope, ActorRef] = Source.actorRef(
      10, OverflowStrategy.dropBuffer
    ).viaMat(Flow.fromSinkAndSourceCoupled(producer, consumer))(Keep.left)
    // scalastyle:on magic.number

    val streamCfg: StreamControllerConfig = StreamControllerConfig(
      name = s"KeepAlive-stream-${bcf.vendor}-${bcf.provider}",
      minDelay = 1.second,
      maxDelay = 1.minute,
      exponential = true,
      onFailureOnly = true,
      random = 0.2
    )

    stream = Some(system.actorOf(
      StreamController.props[FlowEnvelope, ActorRef](keepAliveSource, streamCfg)(onMaterialize = { actor =>
        futMat.complete(Success(actor))
      })
    ))

    futMat.future
  }

  override def stop(): Unit = stream.foreach(system.stop)
}

object JmsKeepAliveActor {
  def props(idSvc : ContainerIdentifierService, cf : IdAwareConnectionFactory, producer : KeepAliveProducerFactory) : Props =
    Props(new JmsKeepAliveActor(idSvc, cf, producer))
}

class JmsKeepAliveActor(
  idSvc : ContainerIdentifierService,
  cf : IdAwareConnectionFactory,
  producer : KeepAliveProducerFactory
) extends Actor
  with JmsStreamSupport {

  private val log : Logger = Logger[JmsKeepAliveActor]
  private implicit val eCtxt : ExecutionContext = context.system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()

  case object Tick

  override def preStart(): Unit = {
    cf match {
      case bcf : BlendedSingleConnectionFactory =>
        if (bcf.config.keepAliveEnabled) {
          producer.createProducer(bcf).onComplete{
            case Success(a) =>
              context.system.scheduler.scheduleOnce(bcf.config.keepAliveInterval, self, Tick)
              context.become(running(a))
            case Failure(t) =>
              log.warn(s"Failed to create Keep Alive producer stream [${t.getMessage()}]")
          }
        } else {
          context.stop(self)
        }

      case _ =>
        log.info(s"No keep alive configuration found for [${cf.vendor}:${cf.provider}]")
        context.stop(self)
    }
  }

  override def postStop(): Unit = producer.stop()

  override def receive: Receive = Actor.emptyBehavior

  private def running(actor : ActorRef) : Receive = {
    case _ =>
  }
}
