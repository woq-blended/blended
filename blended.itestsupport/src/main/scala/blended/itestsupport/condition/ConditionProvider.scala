package blended.itestsupport.condition

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.FiniteDuration

class AlwaysTrue extends Condition {

  val id = ConditionProvider.counter.incrementAndGet().toString

  override def satisfied: Boolean = true
  override val description = s"AlwaysTrueCondition[$id]"
}

class NeverTrue extends Condition {
  val id = ConditionProvider.counter.incrementAndGet().toString
  override def satisfied: Boolean = false
  override val description = s"NeverTrueCondition[$id]"
}

class DelayedTrue(d: FiniteDuration) extends Condition {

  private val id = ConditionProvider.counter.incrementAndGet().toString
  private val created = System.currentTimeMillis()

  override def satisfied: Boolean = (System.currentTimeMillis() - created) >= d.toMillis
  override val description = s"DelayedTrue[$id]"
}

object ConditionProvider {
  val counter = new AtomicInteger()
  def alwaysTrue() = new AlwaysTrue
  def neverTrue() = new NeverTrue
}
