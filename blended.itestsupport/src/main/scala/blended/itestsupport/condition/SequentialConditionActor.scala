package blended.itestsupport.condition

import akka.actor._
import blended.itestsupport.protocol._

object SequentialConditionActor {
  def apply(cond: SequentialComposedCondition) =
    new SequentialConditionActor(cond)
}

class SequentialConditionActor(condition: SequentialComposedCondition) extends Actor with ActorLogging {

  case object SequentialCheck

  var processed : List[Condition] = List.empty
  var remaining : List[Condition] = List.empty

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing : Receive = {
    case CheckCondition => {
      remaining = condition.conditions.toList
      self ! SequentialCheck
      context become checking(sender)
    }
  }

  def checking(checkingFor : ActorRef ) : Receive = {

    case SequentialCheck => {
      remaining match {
        case Nil  => {
          log.debug(s"Successfully processed [${processed.size}] conditions.")
          checkingFor ! new ConditionCheckResult(processed.reverse, List.empty[Condition])
          context stop self
        }
        case x::xs => {
          remaining = xs
          val subChecker = context.actorOf(Props(ConditionActor(x)))
          subChecker ! CheckCondition
        }
      }
    }
    case cr : ConditionCheckResult => {
      cr.allSatisfied match {
        case true =>
          processed = cr.satisfied.head :: processed
          self ! SequentialCheck
        case _ =>
          remaining = cr.timedOut.head :: remaining
          checkingFor ! ConditionCheckResult(processed.reverse, remaining)
          context.stop(self)
      }
    }
  }

  override def toString = s"SequentialConditionActor(${condition}]"
}
