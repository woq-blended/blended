package de.woq.blended.itestsupport.condition

import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import akka.pattern._
import akka.event.LoggingReceive
import akka.util.Timeout
import de.woq.blended.itestsupport.protocol._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object ParallelChecker {
  def apply(conditions: List[Condition]) =
    new ParallelChecker(conditions)
}

class ParallelChecker(conditions: List[Condition]) extends Actor with ActorLogging {

  case class ParallelCheckerResults(results : List[Any])

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing : Receive = LoggingReceive {
    case CheckCondition(d) => {
      conditions match {
        case Nil => sender ! ConditionSatisfied(List.empty)
        case _ => {
          context become checking(sender)
          // Create a single future that terminates when all condition checkers are done
          // The list will be a mix of ConditionSatisfied / ConditionTimeout messages
          Future.sequence(checker(d))
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
  private def checker(d : FiniteDuration) : Seq[Future[Any]] = conditions.map { c =>
    (
      c,                                           // Keep the condition under check in the context
      context.actorOf(Props(ConditionChecker(c)))  // The actor checking a single condition
    )
  }.map { p =>
    implicit val timeout = new Timeout(d)
    (p._2 ? CheckCondition(d)).recover {
      case _ => ConditionTimeOut(List(p._1))
    }
  } toSeq
}
