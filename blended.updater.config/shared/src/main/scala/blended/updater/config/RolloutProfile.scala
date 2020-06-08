package blended.updater.config

case class RolloutProfile(
  profileName : String,
  profileVersion : String,
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
      containerIds = containerIds
    )

}
