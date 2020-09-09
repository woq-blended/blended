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
    states : Seq[FlowTransactionState]
  )

  override def preStart(): Unit = {
    self ! Tick(cfg.retainStale, Seq(FlowTransactionStateStarted, FlowTransactionStateUpdated))
    self ! Tick(cfg.retainCompleted, Seq(FlowTransactionStateCompleted))
    self ! Tick(cfg.retainFailed, Seq(FlowTransactionStateFailed))
  }

  override def receive: Receive = {
    case Tick(i, s) =>
      mgr.cleanUp(s:_*)
      context.system.scheduler.scheduleOnce(i, self, Tick(i, s))
  }
}
