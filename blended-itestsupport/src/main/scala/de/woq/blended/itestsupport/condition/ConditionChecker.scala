package de.woq.blended.itestsupport.condition

import akka.actor._
import akka.event.LoggingReceive
import de.woq.blended.itestsupport.protocol._

import scala.concurrent.duration._

object ConditionChecker {
  def apply(cond : Condition, frequency : FiniteDuration = 100.milliseconds ) =
    new ConditionChecker(cond, frequency)
}

class ConditionChecker(cond: Condition, frequency: FiniteDuration) extends Actor with ActorLogging {

  case object ConditionTimeOut
  case object ConditionCheck

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing : Receive = LoggingReceive {
    case CheckCondition(d) => {
      context become checking(
        sender,
        context.system.scheduler.scheduleOnce(d, self, ConditionTimeOut),
        context.system.scheduler.schedule(1.micro, frequency, self, ConditionCheck)
      )
    }
  }

  def checking(
    checkingFor : ActorRef,
    checker: Cancellable,
    timeoutChecker: Cancellable
  ) : Receive = LoggingReceive {
    case ConditionCheck => {
      cond.satisfied match {
        case true => {
          log.debug(s"Condition [${cond.toString}] is now satisfied.")
          checker.cancel()
          timeoutChecker.cancel()
          checkingFor ! new ConditionSatisfied(cond :: Nil)
        }
        case _ =>
      }
    }
    case ConditionTimeOut => {
      log.debug(s"Condition [${cond.toString}] has timed out.")
      checker.cancel()
      timeoutChecker.cancel()
      checkingFor ! new ConditionTimeOut(cond :: Nil)
    }
  }

}
