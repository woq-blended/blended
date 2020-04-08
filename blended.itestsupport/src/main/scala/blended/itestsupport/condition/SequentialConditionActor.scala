package blended.itestsupport.condition

import scala.concurrent.ExecutionContextExecutor

import akka.actor._
import blended.itestsupport.condition.ConditionActor.CheckCondition
import blended.itestsupport.condition.ConditionActor.ConditionCheckResult

object SequentialConditionActor {
  def props(cond: SequentialComposedCondition): Props = Props(new SequentialConditionActor(cond))
}

class SequentialConditionActor(condition: SequentialComposedCondition) extends Actor with ActorLogging {

  case object SequentialCheck

  var processed: List[Condition] = List.empty
  var remaining: List[Condition] = List.empty

  implicit val eCtxt: ExecutionContextExecutor = context.dispatcher

  def receive: Receive = initializing

  def initializing: Receive = {
    case CheckCondition =>
      remaining = condition.conditions.toList
      self ! SequentialCheck
      context.become(checking(sender()))
  }

  def checking(checkingFor: ActorRef): Receive = {

    case SequentialCheck =>
      remaining match {
        case Nil =>
          log.debug(s"Successfully processed [${processed.size}] conditions.")
          checkingFor ! new ConditionCheckResult(processed.reverse, List.empty)
          context.stop(self)
        case x :: xs =>
          remaining = xs
          val subChecker = context.actorOf(ConditionActor.props(x))
          subChecker ! CheckCondition
      }

    case cr: ConditionCheckResult =>
      cr.allSatisfied match {
        case true =>
          processed = cr.satisfied.head :: processed
          self ! SequentialCheck
        case false =>
          remaining = cr.timedOut.head :: remaining
          checkingFor ! ConditionCheckResult(processed.reverse, remaining)
          context.stop(self)
      }

  }

  override def toString() = s"${getClass().getSimpleName()}(${condition}]"
}
