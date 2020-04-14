package blended.itestsupport.condition

import akka.actor.Actor
import blended.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}

/**
 * An Actor to be used by [[AsyncCondition]].
 */
abstract class AsyncChecker extends Actor {

  import AsyncChecker._

  protected implicit val ctxt : ExecutionContext= context.system.dispatcher
  private[this] val log : Logger = Logger[AsyncChecker]

  case object Tick

  case object Stop

  def performCheck(condition: AsyncCondition): Future[Boolean]

  def receive: Receive = initializing

  def initializing: Receive = {
    case CheckAsyncCondition(condition) =>
      log.debug("Starting asynchronous condition checker")
      self ! Tick
      context.become(checking(condition))
  }

  def checking(condition: AsyncCondition): Receive = {
    case Tick =>
      log.debug(s"Checking asynchronous [${condition.description}] condition ....")
      performCheck(condition).map{
        case true =>
          log.debug(s"Asynchronous condition [${condition.description}] is now satisfied.")
          condition.isSatisfied.set(true)
          context.stop(self)
        case false =>
          log.debug(s"Scheduling next condition check in [${condition.interval}]")
          context.system.scheduler.scheduleOnce(condition.interval, self, Tick)
      }
  }
}

object AsyncChecker {

  /**
   * Use this object to kick off an Asynchronous checker.
   */
  case class CheckAsyncCondition(condition: AsyncCondition)
}