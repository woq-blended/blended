package blended.mgmt.ui.backend

import japgolly.scalajs.react.Callback

trait Observer[T] {

  def update (newData: T) : Unit
}

trait Observable[T] {

  var data : T

  def refresh() : Unit

  var listener : List[Observer[T]] = List.empty

  def addObserver(o : Observer[T]) = Callback {
    listener = o :: listener
    refresh()
  }

  def removeObserver(o: Observer[T]) = Callback {
    listener = listener.filter(_ != o)
  }

  def update(newData : T) : Unit = {
    data = newData
    notifyObservers()
  }

  def notifyObservers() : Unit = {
    listener.foreach(_.update(data))
  }
}

