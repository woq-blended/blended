package blended.mgmt.app

import akka.actor.ActorSystem
import com.github.ahnfelt.react4s.{Component, ReactBridge}
import org.scalajs.dom

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MgmtConsole {

  def main(args: Array[String]) : Unit = {

    lazy val system = ActorSystem()
    implicit val eCtxt : ExecutionContext =  system.dispatcher

    system.scheduler.scheduleOnce(0.millis) {
      system.scheduler.schedule(1.second, 1.second) {
        system.eventStream.publish(Tick())
      }
    }

    dom.window.onload = _ => {
      val comp = Component(SampleComponent, system)
      ReactBridge.renderToDomById(comp, "content")
    }
  }
}
