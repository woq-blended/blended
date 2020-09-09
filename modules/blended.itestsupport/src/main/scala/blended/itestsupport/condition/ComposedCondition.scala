package blended.itestsupport.condition

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration.FiniteDuration

abstract sealed class ComposedCondition(condition: Condition*) extends Condition {
  private val isSatisfied : AtomicBoolean = new AtomicBoolean(false)
  override def satisfied: Boolean = isSatisfied.get()
}

final case class SequentialComposedCondition(conditions: Condition*) extends ComposedCondition(conditions.toSeq:_*) {
  override def timeout: FiniteDuration = conditions.foldLeft(interval * 2)( (sum, c) => sum + c.timeout)
  override val description = s"SequentialComposedCondition(${conditions.toList}})"
}

final case class ParallelComposedCondition(conditions: Condition*) extends ComposedCondition(conditions.toSeq:_*) {
  override def timeout: FiniteDuration = (conditions.foldLeft(interval * 2)((m, c) => if (c.timeout > m) c.timeout else m)) + interval * 2
  override val description = s"ParallelComposedCondition(${conditions.toList}})"
}
