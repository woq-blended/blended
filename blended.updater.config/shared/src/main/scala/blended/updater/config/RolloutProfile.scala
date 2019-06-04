package blended.updater.config

case class RolloutProfile(
  profileName : String,
  profileVersion : String,
  overlays : Set[OverlayRef],
  containerIds : List[String]
)

object RolloutProfile {

  def apply(
    profile : Profile,
    containerIds : List[String]
  ) : RolloutProfile =
    RolloutProfile(
      profileName = profile.name,
      profileVersion = profile.version,
      overlays = profile.overlaySet.overlays,
      containerIds = containerIds
    )

}
