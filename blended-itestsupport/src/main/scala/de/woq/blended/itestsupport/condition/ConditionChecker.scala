package de.woq.blended.itestsupport.condition

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.event.LoggingReceive
import de.woq.blended.itestsupport.protocol._
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.duration._

class DefaultConditionChecker(condition: Condition) extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case ConditionTick => sender ! ConditionCheckResult(condition, condition.satisfied)
  }
}

case object DefaultConditionChecker {
  def apply(condition: Condition) = new DefaultConditionChecker(condition)
}

object ConditionChecker {
  def apply(cond : Condition ) =
    new ConditionChecker(cond, Props(DefaultConditionChecker(cond)))
}

class ConditionChecker(
  cond: Condition,
  checkerProps : Props
) extends Actor with ActorLogging {

  val frequency = (context.system.settings.config.getLong(getClass.getPackage.getName + ".checkfrequency")).millis

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing : Receive = LoggingReceive {
    case CheckCondition(d) => {

      val checker = context.actorOf(checkerProps)

      context become checking(
        sender, checker, d,
        context.system.scheduler.scheduleOnce(d, self, ConditionTimeOut),
        context.system.scheduler.schedule(1.micro, frequency, self, ConditionTick)
      )
    }
  }

  def checking(
    checkingFor    : ActorRef,
    checker        : ActorRef,
    timeout        : FiniteDuration,
    checkTimer     : Cancellable,
    timeoutChecker : Cancellable
  ) : Receive = LoggingReceive {
    case ConditionTick => {
      implicit val t = new Timeout(timeout)
      ( checker ? ConditionTick).mapTo[ConditionCheckResult].pipeTo(self)
    }
    case ConditionCheckResult(condition, satisfied)  => {
      if (satisfied) {
        checkTimer.cancel()
        timeoutChecker.cancel()
        checkingFor ! new ConditionSatisfied(List(condition))
        context.stop(self)
      }
    }
    case ConditionTimeOut => {
      checkTimer.cancel()
      timeoutChecker.cancel()
      checkingFor ! new ConditionTimeOut(List(cond))
      context.stop(self)
    }
  }
}

