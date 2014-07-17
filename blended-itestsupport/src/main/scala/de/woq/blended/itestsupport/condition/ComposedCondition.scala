package de.woq.blended.itestsupport.condition

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{Props, ActorSystem, ActorRef}
import akka.pattern.ask
import de.woq.blended.itestsupport.protocol._

import scala.concurrent.duration.FiniteDuration
import scala.util.Success

abstract class ComposedCondition(conditions: Condition*)(implicit system: ActorSystem, timeout: FiniteDuration) extends Condition {

  var isSatisfied : AtomicBoolean = new AtomicBoolean(false)

  (conditionChecker ? CheckCondition(timeout)).onComplete {
    case Success(result) => { result match {
      case ConditionSatisfied(list) if list == conditions.toList => isSatisfied.set(true)
    }}
    case _ =>
  }

  def conditionChecker : ActorRef
  override def satisfied = isSatisfied.get()
}

class SequentialComposedCondition(conditions: Condition*)
  (implicit system: ActorSystem, timeout: FiniteDuration) extends ComposedCondition {

  override def conditionChecker = system.actorOf(Props(SequentialChecker(conditions.toList)))
}

class ParallelComposedCondition(conditions: Condition*)
  (implicit system: ActorSystem, timeout: FiniteDuration) extends ComposedCondition {

  override def conditionChecker = system.actorOf(Props(ParallelChecker(conditions.toList)))
}