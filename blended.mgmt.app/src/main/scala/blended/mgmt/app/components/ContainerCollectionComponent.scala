package blended.mgmt.app.components

import blended.updater.config.ContainerInfo
import com.github.ahnfelt.react4s._

case class ContainerCollectionComponent(container: P[Map[String, ContainerInfo]]) extends Component[NoEmit] {

  override def render(get: Get): Node = E.div(
    E.div(Tags(
      get(container).map { case (k,v) =>
        E.div(
          Component(ContainerComponent, v).withKey(k)
        )
      }.toSeq
    ))
  )


}
