package blended.mgmt.app

import com.github.ahnfelt.react4s._

import scala.scalajs.js
import scala.scalajs.js.timers.SetIntervalHandle

case class SampleComponent() extends Component[NoEmit] {

  val elapsed = State(0)

  var interval : Option[SetIntervalHandle] = None

  override def componentWillRender(get: Get): Unit = {
    if (interval.isEmpty) interval = Some(js.timers.setInterval(1000) {
      elapsed.modify(_ + 1)
    })
  }


  override def componentWillUnmount(get: Get): Unit = {
    interval.foreach(js.timers.clearInterval)
  }

  override def render(get: Get): Element = {
    E.div(Text(s"${get(elapsed)} seconds elapsed"))
  }
}
