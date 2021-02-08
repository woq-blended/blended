package blended.streams.jms.internal

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorContext, ActorRef, Cancellable, OneForOneStrategy, Props, SupervisorStrategy}
import blended.container.context.api.ContainerContext
import blended.jms.utils._
import blended.streams.BlendedStreamsConfig
import blended.streams.jms.JmsStreamSupport
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext

object JmsKeepAliveController{

  def props(
    ctCtxt : ContainerContext,
    streamsCfg: BlendedStreamsConfig,
    producerFactory : (ContainerContext, BlendedSingleConnectionFactory, BlendedStreamsConfig) => KeepAliveProducerFactory
  ) : Props =
    Props(new JmsKeepAliveController(ctCtxt, streamsCfg, producerFactory))
}

/**
 * Keep track of connection factories registered as services and create KeepAlive Streams as appropriate.
 */
class JmsKeepAliveController(
  ctCtxt : ContainerContext,
  streamsCfg : BlendedStreamsConfig,
  producerFactory : (ContainerContext, BlendedSingleConnectionFactory, BlendedStreamsConfig) => KeepAliveProducerFactory
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
        val producer : KeepAliveProducerFactory = producerFactory(ctCtxt, cf, streamsCfg)

        log.info(s"Starting keep Alive actor for JMS connection factory [${cf.vendor}:${cf.provider}]")
        val actor : ActorRef = context.system.actorOf(JmsKeepAliveActor.props(producer))
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

  def ctCtxt : ContainerContext
  def cf : BlendedSingleConnectionFactory
  def start() : Unit = ()
  def stop() : Unit = ()

  val corrId : String = s"${ctCtxt.uuid}-${cf.vendor}-${cf.provider}"
}

object JmsKeepAliveActor {
  def props(prodFactory : KeepAliveProducerFactory) : Props =
    Props(new JmsKeepAliveActor(prodFactory))
}

class JmsKeepAliveActor(
  prodFactory : KeepAliveProducerFactory
) extends Actor
  with JmsStreamSupport {

  private val log : Logger = Logger[JmsKeepAliveActor]
  private implicit val eCtxt : ExecutionContext = context.system.dispatcher

  private val vendor : String = prodFactory.cf.vendor
  private val provider : String = prodFactory.cf.provider

  private def createTimer(ctx: ActorContext, actor: ActorRef) :  Cancellable = ctx.system.scheduler.scheduleOnce(prodFactory.cf.config.keepAliveInterval, actor, Tick)

  case object Tick

  override def preStart(): Unit = {
    if (prodFactory.cf.config.keepAliveEnabled) {
      context.system.eventStream.subscribe(self, classOf[ProducerMaterialized])
      context.system.eventStream.subscribe(self, classOf[MessageReceived])
      context.system.eventStream.subscribe(self, classOf[ConnectionStateChanged])

      prodFactory.start()
      // We start in idle state
      context.become(idle)
    } else {
      log.info(s"KeepAlive for [$vendor:$provider] is disabled.")
      context.stop(self)
    }
  }

  override def postStop(): Unit = prodFactory.stop()

  override def receive: Receive = Actor.emptyBehavior

  private def resetCounter(actor: ActorRef, current: Int) : Unit = {
    if (current > 0) {
      log.debug(s"Resetting keepAlive counter for [$vendor:$provider]")
      context.system.eventStream.publish(KeepAliveMissed(prodFactory.cf.vendor, prodFactory.cf.provider, prodFactory.corrId, 0))
    }

    val newTimer = createTimer(context, self)
    context.become(running(newTimer, actor, 0))
  }

  private def incCounter(actor : ActorRef, current: Int) : Unit = {

    val newCnt = current + 1

    context.system.eventStream.publish(KeepAliveMissed(prodFactory.cf.vendor, prodFactory.cf.provider, prodFactory.corrId, newCnt))
    log.debug(s"New Keep Alive missed counter for [${prodFactory.cf.vendor}:${prodFactory.cf.provider}] is [$newCnt]")

    val newTimer = createTimer(context, self)
    context.become(running(newTimer, actor, newCnt))
  }

  private def idle : Receive = {
    // we need a materialized producer to start tracking keep alives
    case ProducerMaterialized(v,p,a) =>
      if (v == prodFactory.cf.vendor && p == prodFactory.cf.provider) {
        log.debug(s"Keep alive Stream for [$v:$p] materialized, keep alive interval is [${prodFactory.cf.config.keepAliveInterval}]")
        // Force to kick off the keep alive by using a current value > 0
        resetCounter(a, 1)
      }
    case _ => // do nothing
  }

  private def running(timer: Cancellable, actor : ActorRef, cnt : Int) : Receive = {
    case Tick =>
      if (cnt == prodFactory.cf.config.maxKeepAliveMissed) {
        context.system.eventStream.publish(MaxKeepAliveExceeded(vendor, provider))
      } else  {
        val env : FlowEnvelope = FlowEnvelope(FlowMessage.props(
          "JMSCorrelationID" -> prodFactory.corrId
        ).get)
        log.debug(s"Scheduling keep alive message for [${vendor}:${provider}] : $env")
        actor ! env
        incCounter(actor, cnt)
      }

    case MessageReceived(v, p, _) =>
      if (v == vendor && p == provider) {
        timer.cancel()
        resetCounter(actor, cnt)
      }

    case ConnectionStateChanged(state) =>
      if (state.status != Connected) {
        log.debug(s"Suspending keep alive monitor for [${vendor}:${provider}] in state [${state.status}]")
        timer.cancel()
        context.become(idle)
      }

    case ProducerMaterialized(v, p, a) =>
      if (v == vendor && p == provider) {
        log.debug(s"Keep alive Stream for [$v:$p] materialized, keep alive interval is [${prodFactory.cf.config.keepAliveInterval}]")
        resetCounter(a, 1)
      }
  }
}
