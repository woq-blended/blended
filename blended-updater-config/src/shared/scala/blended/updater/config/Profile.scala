package blended.updater.config

case class Profile(name: String, version: String, overlays: Seq[OverlaySet])
