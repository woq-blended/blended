package blended.streams.jms.internal

import akka.actor.{Actor, ActorRef, Props}
import blended.jms.utils._
import blended.util.logging.Logger

class JmsKeepAliveController extends Actor {

  private val log : Logger = Logger[JmsKeepAliveController]

  override def preStart(): Unit =
    context.become(running(Map.empty))

  private val cfKey : BlendedJMSConnectionConfig => String = cfCfg => s"${cfCfg.vendor}${cfCfg.provider}"

  override def receive: Receive = Actor.emptyBehavior

  private def running(watched : Map[String, ActorRef]) : Receive = {
    case AddedConnectionFactory(cfCfg) =>
      if (!watched.contains(cfKey(cfCfg)) && cfCfg.keepAliveEnabled) {
        log.info(s"Starting keep Alive actor for JMS connection factory ")
        val actor : ActorRef = context.system.actorOf(JmsKeepAliveActor.props(cfCfg))
        context.become(running(watched + (cfKey(cfCfg) -> actor)))
      }

    case RemovedConnectionFactory(cfCfg) => watched.get(cfKey(cfCfg)).foreach { actor =>
      log.info(s"Stopping keep Alive Actor for JMS connection factory")
      context.system.stop(actor)
      context.become(running(watched - cfKey(cfCfg)))
    }
  }
}

object JmsKeepAliveActor {

  def props(cfCfg : BlendedJMSConnectionConfig) : Props =
    Props(new JmsKeepAliveActor(cfCfg))
}

class JmsKeepAliveActor(cfCfg : BlendedJMSConnectionConfig) extends Actor {
  override def receive: Receive = Actor.emptyBehavior
}
