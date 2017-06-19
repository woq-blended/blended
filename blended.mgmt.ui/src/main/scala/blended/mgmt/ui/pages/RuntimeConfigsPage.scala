package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.components.ProfilesComp
import blended.mgmt.ui.components.RuntimeConfigsComp

object RuntimeConfigsPage extends TopLevelPage {

  override val name: String = i18n.tr("Runtime Configs")

  override val routerPath: Path = Path("#runtimeConfigs")

  override val content: ReactElement = <.p(
    <.div(RuntimeConfigsComp.Component())
  )
}
