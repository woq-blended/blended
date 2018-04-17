package blended.mgmt.app

import com.github.ahnfelt.react4s.{Component, ReactBridge}
import org.scalajs.dom

object MgmtConsole {

  def main(args: Array[String]) : Unit = {

    dom.window.onload = _ => {
      val comp = Component(SampleComponent)
      ReactBridge.renderToDomById(comp, "content")
    }
  }
}
