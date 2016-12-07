package blended.itestsupport.condition

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem, Props}
import blended.itestsupport.protocol.CheckAsyncCondition

import scala.concurrent.duration.FiniteDuration

object AsyncCondition{
  def apply(asyncChecker: Props, desc: String, t: Option[FiniteDuration] = None)(implicit system: ActorSystem) = t match {
    case None => new AsyncCondition(asyncChecker, desc)
    case Some(d) => new AsyncCondition(asyncChecker, desc) {
      override def timeout = d
    }
  }
}

class AsyncCondition(asyncChecker: Props, desc: String)(implicit val system: ActorSystem) extends Condition {

  var checker : Option[ActorRef] = None

  val isSatisfied = new AtomicBoolean(false)

  override def satisfied = {
    checker match {
      case None =>
        checker = Some(system.actorOf(asyncChecker))
        checker.get ! CheckAsyncCondition(this)
      case _ =>
    }
    isSatisfied.get()
  }

  override val description: String = desc
}
