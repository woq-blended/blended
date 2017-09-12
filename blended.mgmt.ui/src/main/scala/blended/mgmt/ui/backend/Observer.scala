package blended.mgmt.ui.backend

import blended.mgmt.ui.util.Logger
import japgolly.scalajs.react.Callback

trait Observer[T] {

  val dataChanged : T => Callback
}

trait Observable[T] {

  private[this] val log = Logger(getClass.getName())
  var data : T

  def refresh() : Unit

  var listener : List[Observer[T]] = List.empty

  def addObserver(o : Observer[T]) = Callback {
    listener = o :: listener
    log.trace(s"actual [${listener.size}] observers")
    refresh()
  }

  def removeObserver(o: Observer[T]) = Callback {
    listener = listener.filter(_ != o)
    log.trace(s"actual [${listener.size}] observers")
  }

  def update(newData : T) : Unit = {
    data = newData
    notifyObservers()
  }

  def notifyObservers() : Unit = {
    listener.foreach(_.dataChanged(data).runNow())
  }
}

