package blended.streams.jms.internal

import akka.actor
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.stream.{ActorMaterializer, Materializer}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils._
import blended.streams.jms.JmsStreamSupport
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object JmsKeepAliveController{
  def props(idSvc : ContainerIdentifierService, producer : KeepAliveProducerFactory) : Props =
    Props(new JmsKeepAliveController(idSvc, producer))
}

/**
 * Keep track of connection factories registered as services and create KeepAlive Streams as appropriate.
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
        context.become(running(watched + (cfKey(cf) -> actor)))
      }

    case RemovedConnectionFactory(cf) => watched.get(cfKey(cf)).foreach { actor =>
      log.info(s"Stopping keep Alive Actor for JMS connection factory [${cf.vendor}:${cf.provider}]")
      context.system.stop(actor)
      context.become(running(watched - cfKey(cf)))
    }
  }
}

trait KeepAliveProducerFactory {
  val createProducer : BlendedSingleConnectionFactory => Future[ActorRef]
  def stop() : Unit = ()
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
  private val headerConfig : FlowHeaderConfig = FlowHeaderConfig.create(idSvc)

  case object Tick

  override def preStart(): Unit = {
    cf match {
      case bcf : BlendedSingleConnectionFactory =>
        if (bcf.config.keepAliveEnabled) {
          producer.createProducer(bcf).onComplete{
            case Success(a) =>
              val timer : actor.Cancellable = context.system.scheduler.scheduleOnce(bcf.config.keepAliveInterval, self, Tick)

              context.system.eventStream.subscribe(self, classOf[MessageReceived])
              context.system.eventStream.subscribe(self, classOf[ConnectionStateChanged])

              setCounter(bcf.config, a, 0)
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

  private def setCounter(cfg : ConnectionConfig, actor : ActorRef, newCnt : Int, oldCnt : Option[Int] = None) : Unit = {
    if (oldCnt.isEmpty || !oldCnt.contains(newCnt)) {
      context.system.eventStream.publish(KeepAliveMissed(cf.vendor, cf.provider, newCnt))
      val newTimer = context.system.scheduler.scheduleOnce(cfg.keepAliveInterval, self, Tick)
      context.become(running(newTimer, cfg, actor, newCnt))
    }
  }

  private def idle(cfg : ConnectionConfig, actor : ActorRef) : Receive = {
    case ConnectionStateChanged(state) if state.status == Connected =>
      setCounter(cfg, actor, 0)
  }

  private def running(timer: Cancellable, cfg : ConnectionConfig, actor : ActorRef, cnt : Int) : Receive = {
    case Tick if cnt == cfg.maxKeepAliveMissed =>
      context.system.eventStream.publish(MaxKeepAliveExceeded(cf.vendor, cf.provider))

    case Tick =>
      val env : FlowEnvelope = FlowEnvelope(FlowMessage.props(
        "JMSCorrelationID" -> idSvc.uuid,
        headerConfig.headerKeepAlivesMissed -> (cnt + 1)
      ).get)

      actor ! env

      setCounter(cfg, actor, cnt + 1, Some(cnt))

    case MessageReceived(v, p, _) if v == cf.vendor && p == cf.provider =>
      timer.cancel()
      setCounter(cfg,actor,0,Some(cnt))

    case ConnectionStateChanged(state) if state.status != Connected =>
      timer.cancel()
      context.become(idle(cfg, actor))
  }
}
