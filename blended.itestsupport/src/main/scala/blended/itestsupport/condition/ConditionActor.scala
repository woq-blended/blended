package blended.itestsupport.condition

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import blended.itestsupport.protocol._

object ConditionActor {
  def apply(cond: Condition) = cond match {
    case pc : ParallelComposedCondition => new ParallelConditionActor(pc)
    case sc : SequentialComposedCondition => new SequentialConditionActor(sc)
    case _ => new ConditionActor(cond)
  }
}

class ConditionActor(cond: Condition) extends Actor with ActorLogging {

  case object Tick
  case object Check

  implicit val ctxt = context.system.dispatcher

  def receive = initializing

  def initializing : Receive = {
    case CheckCondition =>
      log.debug(s"Checking condition [${cond.description}] on behalf of [${sender}]")
      val timer = context.system.scheduler.scheduleOnce(cond.timeout, self, Tick)
      context.become(checking(sender, timer))
      self ! Check
  }

  def checking(checkingFor: ActorRef, timer: Cancellable) : Receive = {
    case CheckCondition =>
      log.warning(
        s"""
           |
           |You have sent another CheckCondition message from [${sender}],
           |but this actor is already checking on behalf of [${checkingFor}].
           |
         """)
    case Check => cond.satisfied match {
      case true =>
        log.info(s"Condition [${cond}] is now satisfied.")
        timer.cancel()
        val response = ConditionCheckResult(List(cond), List.empty[Condition])
        log.debug(s"Answering [${response}] to [${checkingFor}]")
        checkingFor ! response
        context.stop(self)
      case false =>
        context.system.scheduler.scheduleOnce(cond.interval, self, Check)
    }
    case Tick  =>
      log.info(s"Condition [${cond}] hast timed out.")
      log.debug(s"Answering to [${checkingFor}]")
      checkingFor ! ConditionCheckResult(List.empty[Condition], List(cond))
      context.stop(self)
  }
}
