package blended.mgmt.ui.components.filter

import blended.updater.config.ContainerInfo

trait Filter[T] {
  def matches(element: T): Boolean
  def filter(elements: Traversable[T]): List[T] = elements.filter(matches).toList
}

case class And[T](filters: Filter[T]*) extends Filter[T] {
  override def matches(e: T): Boolean = filters.forall { f => f.matches(e) }
  override def toString(): String = getClass().getSimpleName() + filters.mkString("(", ",", ")")
  def normalized: And[T] = {
    filters match {
      case Seq() => this
      //      case Seq(inner) => inner
      case x =>
        val fs = x.map {
          case and: And[_] => and.normalized
          case other => other
        }.flatMap {
          case and: And[_] => and.filters
          case other => Seq(other)
        }
        And(fs: _*)
    }
  }
  def append(filters: Filter[T]*): And[T] = {
    And((this.filters ++ filters): _*)
  }
}

case class Or[T](filters: Filter[T]*) extends Filter[T] {
  override def matches(e: T): Boolean = filters.exists { f => f.matches(e) }
  override def toString(): String = getClass().getSimpleName() + filters.mkString("(", ",", ")")
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

