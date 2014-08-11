package de.woq.blended.itestsupport.condition

import scala.language.postfixOps
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import akka.pattern._
import de.woq.blended.itestsupport.protocol._

import scala.concurrent.Future

object ParallelChecker {
  def apply(conditions: List[Condition]) =
    new ParallelChecker(conditions)
}

class ParallelChecker(conditions: List[Condition]) extends Actor with ActorLogging {

  case class ParallelCheckerResults(results : List[Any])

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing : Receive = LoggingReceive {
    case CheckCondition => {
      conditions match {
        case Nil => sender ! ConditionSatisfied(List.empty)
        case _ => {
          context become checking(sender)
          // Create a single future that terminates when all condition checkers are done
          // The list will be a mix of ConditionSatisfied / ConditionTimeout messages
          Future.sequence(checker)
            .mapTo[List[Any]]
            .map(new ParallelCheckerResults(_))
            .pipeTo(self)
        }
      }
    }
  }

  def checking(checkingFor: ActorRef) : Receive = LoggingReceive {
    case ParallelCheckerResults(results) => {
      // get everything that succeeded
      val succeeded = results.filter { _ match {
        case s : ConditionSatisfied => true
        case _ => false
      }}.asInstanceOf[List[ConditionSatisfied]]

      // get everything that timed out
      val timedOut = results.filter { _ match {
        case s : ConditionSatisfied => false
        case _ => true
      }}.asInstanceOf[List[ConditionTimeOut]]

      // If we have any timeout we respond with a timeout otherwise we succeeded
      // Note that each message contains only a one element condition list and we collect all the
      // elements in a single list
      timedOut match {
        case Nil => checkingFor ! new ConditionSatisfied(succeeded.map { _.conditions.head })
        case _ => checkingFor ! new ConditionTimeOut( timedOut.map { _.conditions.head} )
      }

      context stop self
    }
  }

  // Create a list of Future that execute in parallel
  private def checker : Seq[Future[Any]] = conditions.map { c =>
    (
      c,                                           // Keep the condition under check in the context
      context.actorOf(Props(ConditionChecker(c)))  // The actor checking a single condition
    )
  }.map { p =>
    (p._2 ? CheckCondition)(p._1.timeout).recover {
      case _ => ConditionTimeOut(List(p._1))
    }
  } toSeq
}
