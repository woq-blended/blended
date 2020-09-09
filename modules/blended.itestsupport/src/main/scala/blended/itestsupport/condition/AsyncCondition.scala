package blended.itestsupport.condition

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem, Props}
import scala.concurrent.duration.FiniteDuration

import blended.itestsupport.condition.AsyncChecker.CheckAsyncCondition

object AsyncCondition {
  def apply(
    asyncChecker: Props,
    desc: String,
    timeout: Option[FiniteDuration] = None
  )(implicit system: ActorSystem): AsyncCondition = timeout match {
    case None => new AsyncCondition(asyncChecker, desc)
    case Some(d) => new AsyncCondition(asyncChecker, desc) {
      override def timeout = d
    }
  }
}

/**
 * A [[Condition]] that checks it's condition asynchronously by utilizing an [[AsyncChecker]].
 * To implement your own async condition, derive from [[AsyncChecker]].
 */
class AsyncCondition(asyncCheckerProps: Props, desc: String)(implicit val system: ActorSystem)
  extends Condition {

  var checker: Option[ActorRef] = None

  val isSatisfied = new AtomicBoolean(false)

  override def satisfied = {
    checker match {
      case None =>
        checker = Some(system.actorOf(asyncCheckerProps))
        checker.get ! CheckAsyncCondition(this)
      case _ =>
    }
    isSatisfied.get()
  }

  override val description: String = desc
}
