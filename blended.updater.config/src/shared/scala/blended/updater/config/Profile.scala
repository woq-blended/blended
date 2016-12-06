package blended.updater.config

import upickle.default._

case class Profile(name: String, version: String, overlays: List[OverlaySet])

object Profile {
  implicit val readWriter: ReadWriter[Profile] = macroRW
}
