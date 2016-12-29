package blended.mgmt.ui.backend

import blended.mgmt.ui.ConsoleSettings
import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.Callback
import org.scalajs.dom.ext.Ajax

import prickle._
import blended.updater.config.json.PrickleProtocol._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.Success

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

object DataManager {

  object containerData extends Observable[List[ContainerInfo]] {

    override var data: List[ContainerInfo] = List.empty

    override def refresh(): Unit = {

      Ajax.get(ConsoleSettings.mgmtUrl).onComplete {
        case Success(xhr) =>
          println(xhr.responseText)
          update(Unpickle[List[ContainerInfo]].fromString(xhr.responseText).get)
        case _ => println("Could not retrieve container list from server")
      }
    }

  }
}

