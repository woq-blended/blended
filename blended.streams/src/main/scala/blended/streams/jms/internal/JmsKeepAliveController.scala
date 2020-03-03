package blended.streams.jms.internal

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, Props, SupervisorStrategy}
import akka.stream.{ActorMaterializer, Materializer}
import blended.container.context.api.ContainerContext
import blended.jms.utils._
import blended.streams.FlowHeaderConfig
import blended.streams.jms.JmsStreamSupport
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext

object JmsKeepAliveController{
  def props(ctCtxt : ContainerContext, producer : KeepAliveProducerFactory) : Props =
    Props(new JmsKeepAliveController(ctCtxt, producer))
}

/**
 * Keep track of connection factories registered as services and create KeepAlive Streams as appropriate.
 */
class JmsKeepAliveController(
  ctCtxt : ContainerContext,
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
        val actor : ActorRef = context.system.actorOf(JmsKeepAliveActor.props(ctCtxt, cf, producer))
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
  def start(bcf : BlendedSingleConnectionFactory) : Unit = ()
  def stop() : Unit = ()
}

object JmsKeepAliveActor {
  def props(ctCtxt : ContainerContext, cf : IdAwareConnectionFactory, prodFactory : KeepAliveProducerFactory) : Props =
    Props(new JmsKeepAliveActor(ctCtxt, cf, prodFactory))
}

class JmsKeepAliveActor(
  ctCtxt : ContainerContext,
  cf : IdAwareConnectionFactory,
  prodFactory : KeepAliveProducerFactory
) extends Actor
  with JmsStreamSupport {

  private val log : Logger = Logger[JmsKeepAliveActor]
  private implicit val eCtxt : ExecutionContext = context.system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()
  private val headerConfig : FlowHeaderConfig = FlowHeaderConfig.create(ctCtxt)

  case object Tick

  override def preStart(): Unit = {
    cf match {
      case bcf : BlendedSingleConnectionFactory =>
        if (bcf.config.keepAliveEnabled) {

          context.system.eventStream.subscribe(self, classOf[ProducerMaterialized])
          context.system.eventStream.subscribe(self, classOf[MessageReceived])
          context.system.eventStream.subscribe(self, classOf[ConnectionStateChanged])

          prodFactory.start(bcf)
          // We start in idle state
          context.become(idle(bcf.config))
        } else {
          log.info(s"KeepAlive for [${bcf.vendor}:${bcf.provider}] is disabled.")
          context.stop(self)
        }

      case _ =>
        log.info(s"No keep alive configuration found for [${cf.vendor}:${cf.provider}]")
        context.stop(self)
    }
  }

  override def postStop(): Unit = prodFactory.stop()

  override def receive: Receive = Actor.emptyBehavior

  private def setCounter(cfg : ConnectionConfig, actor : ActorRef, newCnt : Int, oldCnt : Option[Int] = None) : Unit = {
    if (oldCnt.isEmpty || !oldCnt.contains(newCnt)) {
      context.system.eventStream.publish(KeepAliveMissed(cf.vendor, cf.provider, newCnt))
      log.debug(s"New Keep Alive missed counter for [${cf.vendor}:${cf.provider}] is [$newCnt]")
    }

    val newTimer = context.system.scheduler.scheduleOnce(cfg.keepAliveInterval, self, Tick)
    context.become(running(newTimer, cfg, actor, newCnt))
  }

  private def idle(cfg : ConnectionConfig) : Receive = {
    // we need a materialized producer to start tracking keep alives
    case ProducerMaterialized(v,p,a) =>
      if (v == cfg.vendor && p == cfg.provider) {
        log.debug(s"Keep alive Stream for [$v:$p] materialized, keep alive interval is [${cfg.keepAliveInterval}]")
        setCounter(cfg, a, 0)
      }
    case _ => // do nothing
  }

  private def running(timer: Cancellable, cfg : ConnectionConfig, actor : ActorRef, cnt : Int) : Receive = {
    case Tick if cnt == cfg.maxKeepAliveMissed =>
      context.system.eventStream.publish(MaxKeepAliveExceeded(cf.vendor, cf.provider))

    case Tick =>
      val env : FlowEnvelope = FlowEnvelope(FlowMessage.props(
        "JMSCorrelationID" -> ctCtxt.uuid,
        headerConfig.headerKeepAlivesMissed -> (cnt + 1)
      ).get)

      log.debug(s"Scheduling keep alive message for [${cf.vendor}:${cf.provider}] : [$env]")
      actor ! env
      setCounter(cfg, actor, cnt + 1, Some(cnt))

    case MessageReceived(v, p, _) if v == cf.vendor && p == cf.provider =>
      timer.cancel()
      setCounter(cfg,actor,0,Some(cnt))

    case ConnectionStateChanged(state) if state.status != Connected =>
      timer.cancel()
      context.become(idle(cfg))

    case ProducerMaterialized(v, p, a) =>
      if (v == cfg.vendor && p == cfg.provider) {
        log.debug(s"Keep alive Stream for [$v:$p] materialized, keep alive interval is [${cfg.keepAliveInterval}]")
        setCounter(cfg, a, 0)
      }
  }
}
