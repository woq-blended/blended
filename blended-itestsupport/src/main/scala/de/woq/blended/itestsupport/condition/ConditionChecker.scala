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

  case object TimeOut
  case object Check

  var checker : Option[Cancellable] = None
  var timeoutWatcher : Option[Cancellable] = None
  var checkingFor : Option[ActorRef] = None

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing : Receive = LoggingReceive {
    case CheckCondition(d) => {
      checkingFor = Some(sender)
      timeoutWatcher = Some(context.system.scheduler.scheduleOnce(d, self, TimeOut))
      checker = Some(context.system.scheduler.schedule(1.micro, frequency, self, Check))
      context become checking
    }
  }

  def checking : Receive = LoggingReceive {
    case Check => {
      cond.satisfied() match {
        case true => {
          log.debug(s"Condition [${cond.toString}] is now satisfied.")
          stopChecking(new ConditionSatisfied(cond :: Nil))
        }
        case _ =>
      }
    }
    case TimeOut => {
      log.debug(s"Condition [${cond.toString}] has timed out.")
      stopChecking(new ConditionTimeOut(cond :: Nil))
    }
  }

  private def stopChecking(msg: AnyRef) {
    checker.foreach(_.cancel())
    timeoutWatcher.foreach(_.cancel())
    checkingFor.foreach(_.tell(msg, self))
    context.stop(self)
  }
}
