package de.woq.blended.itestsupport.condition

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{Props, ActorSystem, ActorRef}
import akka.pattern.ask
import de.woq.blended.itestsupport.protocol._

import scala.util.Success

abstract class ComposedCondition(conditions: Condition*)(implicit system: ActorSystem) extends Condition {

  var isSatisfied : AtomicBoolean = new AtomicBoolean(false)

  implicit val eCtxt = system.dispatcher

  (conditionChecker ? CheckCondition)(timeout).onComplete {
    case Success(result) => { result match {
      case ConditionSatisfied(_) => isSatisfied.set(true)
      case _ =>
    }}
    case _ =>
  }

  def conditionChecker : ActorRef
  override def satisfied = isSatisfied.get()
}

class SequentialComposedCondition(conditions: Condition*)
  (implicit system: ActorSystem) extends ComposedCondition {

  override def timeout = conditions.foldLeft(interval * 2)( (sum, c) => sum + c.timeout)
  override def conditionChecker = system.actorOf(Props(SequentialChecker(conditions.toList)))

  override def toString = s"SequentialComposedCondition(${conditions.toList}})"
}

class ParallelComposedCondition(conditions: Condition*)
  (implicit system: ActorSystem) extends ComposedCondition {

  override def timeout = (conditions.foldLeft(interval * 2)((m, c) => if (c.timeout > m) c.timeout else m)) + interval * 2

  override def conditionChecker = system.actorOf(Props(ParallelChecker(conditions.toList)))

  override def toString = s"ParallelComposedCondition(${conditions.toList}})"
}