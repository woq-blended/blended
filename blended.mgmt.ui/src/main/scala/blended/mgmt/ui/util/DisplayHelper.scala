package blended.mgmt.ui.util

import blended.updater.config.Profile.SingleProfile

object DisplayHelper {

  val i18n = I18n()

  def profileToString(p: SingleProfile) : String = {

    val overlays = if (p.overlays.isEmpty) i18n.tr("without overlays") else p.overlays.mkString(", ")

    s"${p.name}-${p.version} $overlays"
  }
}
