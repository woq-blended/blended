package blended.mgmt.ui.backend

import blended.mgmt.ui.ConsoleSettings
import blended.updater.config.ContainerInfo
import org.scalajs.dom.ext.Ajax

import prickle._
import blended.updater.config.json.PrickleProtocol._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.Success


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

