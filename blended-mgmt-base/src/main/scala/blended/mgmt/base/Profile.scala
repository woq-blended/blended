package blended.mgmt.base

case class Profile(name: String, version: String, overlays: Seq[OverlaySet])
