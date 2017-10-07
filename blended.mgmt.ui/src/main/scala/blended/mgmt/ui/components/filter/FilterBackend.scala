package blended.mgmt.ui.components.filter

import japgolly.scalajs.react.BackendScope

object FilterState {
  def apply[T](
    items : List[T] = List.empty,
    filter : And[T] = And[T](),
    selected : Option[T] = None
  ) : FilterState[T] = new FilterState[T](items, filter, selected)
}

class FilterState[T](
  i : List[T],
  f : And[T],
  s : Option[T]
) {

  val items = i
  val filter = f
  val selected = s

  def copy(
    items : List[T] = items,
    filter : And[T] = filter,
    selected : Option[T] = selected
  ) = FilterState[T] (items, filter, selected)

  def filteredList : List[T] = items.filter { i => filter.matches(i) }
  def consistent = this.copy(selected = selected.filter(s => items.filter(i => filter.matches(i)).exists(_ == s)))
  def clearFilter = copy(filter = And()).consistent

}

trait FilterBackend[P,T] {

  val scope: BackendScope[P, FilterState[T]]

  def addFilter(filter: Filter[T]) = {
    scope.modState { s =>
      val newFilter = s.filter.append(filter).normalized
      s.copy(filter = newFilter).consistent
    }.runNow()
  }

  def removeFilter(filter: Filter[T]) = {
    scope.modState(s => s.copy(filter = And((s.filter.filters.filter(_ != filter)): _*)).consistent).runNow()
  }

  def removeAllFilter() = {
    scope.modState(s => s.clearFilter).
      runNow()
  }

  def selectItem(item: Option[T]) = {
    scope.modState(s => s.copy(selected = item).consistent).runNow()
  }

}
