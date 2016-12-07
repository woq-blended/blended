package blended.itestsupport.condition

import akka.actor.{Actor, ActorLogging}
import blended.itestsupport.protocol.CheckAsyncCondition

import scala.concurrent.Future

abstract class AsyncChecker extends Actor with ActorLogging {

  protected implicit val ctxt = context.system.dispatcher

  case object Tick
  case object Stop

  def performCheck(condition: AsyncCondition) : Future[Boolean]

  def receive = initializing

  def initializing : Receive = {
    case CheckAsyncCondition(condition) =>
      log.debug("Starting asynchronous condition checker")
      self ! Tick
      context.become(checking(condition))
  }

  def checking(condition: AsyncCondition) : Receive = {
    case Tick =>
      log.debug(s"Checking asynchronous [${condition.description}] condition ....")
      performCheck(condition).map(_ match {
        case true =>
          log.debug(s"Asynchronous condition [${condition.description}] is now satisfied.")
          condition.isSatisfied.set(true)
          context.stop(self)
        case false =>
          log.debug(s"Scheduling next condition check in [${condition.interval}]")
          context.system.scheduler.scheduleOnce(condition.interval, self, Tick)
      })
  }
}
