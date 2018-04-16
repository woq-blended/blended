package blended.mgmt.app

import org.scalajs.dom

object MgmtConsole {

  def main(args: Array[String]) : Unit = {

    val content = dom.document.getElementById("content")
    val comp = SampleComponent("Blended rocks !")

    comp.renderIntoDOM(content)

  }
}
