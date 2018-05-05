package blended.mgmt.app

import com.github.ahnfelt.react4s.{Component, ReactBridge}
import org.scalajs.dom

object MgmtConsoleLoader {

  def main(args: Array[String]) : Unit = {

    dom.window.onload = _ => {
      val comp = Component(Main)
      ReactBridge.renderToDomById(comp, "content")
    }
  }
}
