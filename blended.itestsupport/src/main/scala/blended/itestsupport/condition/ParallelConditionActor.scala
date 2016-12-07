package blended.itestsupport.condition

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import blended.itestsupport.protocol._

import scala.concurrent.Future
import scala.language.postfixOps

object ParallelConditionActor {
  def apply(condition: ParallelComposedCondition) =
    new ParallelConditionActor(condition)
}

class ParallelConditionActor(condition: ParallelComposedCondition) extends Actor with ActorLogging {

  case class ParallelCheckerResults(results : List[Any])

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing : Receive = {
    case CheckCondition => {
      condition.conditions.toSeq match {
        case Nil => sender ! ConditionCheckResult(List.empty[Condition], List.empty[Condition])
        case _ => {
          context become checking(sender)
          // Create a single future that terminates when all condition checkers are done
          // The list will be a mix of ConditionSatisfied / ConditionTimeout messages
          Future.sequence(checker)
            .mapTo[Seq[Any]]
            .map(seq => new ParallelCheckerResults(seq.toList))
            .pipeTo(self)
        }
      }
    }
  }

  def checking(checkingFor: ActorRef) : Receive = {
    case ParallelCheckerResults(results) => {
      log.debug(s"Received ParallelCheckerResults [${results}]")
      val combinedResult = ConditionCheckResult(results.asInstanceOf[List[ConditionCheckResult]])
      log.debug(s"Answering to [${checkingFor}] : [${combinedResult}]")
      checkingFor ! combinedResult
      context stop self
    }
  }

  // Create a list of Future that execute in parallel
  private def checker : Seq[Future[Any]] = condition.conditions.toSeq.map { c =>
    (
      c,                                         // Keep the condition under check in the context
      context.actorOf(Props(ConditionActor(c)))  // The actor checking a single condition
    )
  }.map { p =>
    (p._2 ? CheckCondition)(p._1.timeout).recover {
      case _ => ConditionCheckResult(List.empty[Condition], List(p._1))
    }
  }

  override def toString = s"ParallelConditionActor(${condition})"
}
