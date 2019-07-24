package blended.streams.transaction

import akka.actor.{Actor, Props}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object TransactionManagerCleanupActor {

  def props(mgr : FlowTransactionManager, cfg : FlowTransactionManagerConfig) : Props =
    Props(new TransactionManagerCleanupActor(mgr, cfg))
}

class TransactionManagerCleanupActor(
  mgr : FlowTransactionManager,
  cfg : FlowTransactionManagerConfig
) extends Actor {

  private implicit val eCtxt : ExecutionContext = context.system.dispatcher

  case class Tick(
    interval: FiniteDuration,
    states : FlowTransactionState*
  )

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(cfg.retainStale, self, Tick(cfg.retainStale, FlowTransactionStateStarted, FlowTransactionStateUpdated))
    context.system.scheduler.scheduleOnce(cfg.retainCompleted, self, Tick(cfg.retainCompleted, FlowTransactionStateCompleted))
    context.system.scheduler.scheduleOnce(cfg.retainFailed, self, Tick(cfg.retainFailed, FlowTransactionStateFailed))
  }

  override def receive: Receive = {
    case Tick(i, s) =>
      mgr.cleanUp(s)
      context.system.scheduler.scheduleOnce(i, self, Tick(i, s))
  }
}
