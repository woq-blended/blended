package de.woq.blended.itestsupport.condition

import akka.actor
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
      val succeeded = results.filter { _ match {
        case s : ConditionSatisfied => true
        case _ => false
      }}.asInstanceOf[List[ConditionSatisfied]]

      val timedOut = results.filter { _ match {
        case s : ConditionSatisfied => false
        case _ => true
      }}.asInstanceOf[List[ConditionTimeOut]]

      timedOut match {
        case Nil => checkingFor ! new ConditionSatisfied(succeeded.map { _.conditions.head })
        case _ => checkingFor ! new ConditionTimeOut( timedOut.map { _.conditions.head} )
      }
    }
  }

  private def checker(d : FiniteDuration) : Seq[Future[Any]] = conditions.map { c =>
    (
      c,
      context.actorOf(Props(ConditionChecker(c)))
    )
  }.map { p =>
    implicit val timeout = new Timeout(d)
    (p._2 ? CheckCondition(d)).recover {
      case _ => ConditionTimeOut(List(p._1))
    }
  } toSeq
}
