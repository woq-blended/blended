package blended.itestsupport.condition

import java.util.concurrent.atomic.AtomicBoolean

abstract class ComposedCondition(condition: Condition*) extends Condition {

  private var isSatisfied : AtomicBoolean = new AtomicBoolean(false)
  override def satisfied = isSatisfied.get()
}

case class SequentialComposedCondition(conditions: Condition*) extends ComposedCondition(conditions.toSeq:_*) {
  override def timeout = conditions.foldLeft(interval * 2)( (sum, c) => sum + c.timeout)
  override val description = s"SequentialComposedCondition(${conditions.toList}})"
}

case class ParallelComposedCondition(conditions: Condition*) extends ComposedCondition(conditions.toSeq:_*) {
  override def timeout = (conditions.foldLeft(interval * 2)((m, c) => if (c.timeout > m) c.timeout else m)) + interval * 2
  override val description = s"ParallelComposedCondition(${conditions.toList}})"
}
