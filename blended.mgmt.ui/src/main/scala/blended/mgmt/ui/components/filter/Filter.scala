package blended.mgmt.ui.components.filter

import blended.updater.config.ContainerInfo

trait Filter[T] {
  def matches(element: T): Boolean
  def filter(elements: Traversable[T]): List[T] = elements.filter(matches).toList
}

case class And[T](filters: Filter[T]*) extends Filter[T] {
  override def matches(e: T): Boolean = filters.forall { f => f.matches(e) }
}

case class Or[T](filters: Filter[T]*) extends Filter[T] {
  override def matches(e: T): Boolean = filters.exists { f => f.matches(e) }
}

case class Not[T](filter: Filter[T]) extends Filter[T] {
  override def matches(e: T): Boolean = !filter.matches(e)
}

case object True extends Filter[Any] {
  override def matches(e: Any): Boolean = true
}

case object False extends Filter[Any] {
  override def matches(e: Any): Boolean = false
}

