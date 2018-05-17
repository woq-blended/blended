package blended.mgmt.app.components

import blended.updater.config.ContainerInfo
import com.github.ahnfelt.react4s._

import scala.scalajs.js.Date

case class ContainerComponent(ctInfo: P[ContainerInfo]) extends Component[NoEmit] {

  override def render(get: Get): Node = {

    val d : String = new Date(get(ctInfo).timestampMsec).toISOString()

    E.div(
      Text(s"Container: ${get(ctInfo).containerId}, lastUpdate: $d")
    )
  }
}
