package blended.updater.config

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.prop.PropertyChecks

class ProfileSpec extends LoggingFreeSpec with PropertyChecks {

  import TestData._

  "Profile maps to SingleProfile" in {
    forAll { profile: Profile =>
      val singles = profile.toSingle
      assert(singles.size === profile.overlays.size)
      assert(Profile.fromSingleProfiles(singles) === List(profile))
    }
  }

  "SingleProfile maps to Profile" in {
    forAll{singles: Seq[SingleProfile] =>
      val profiles = Profile.fromSingleProfiles(singles)
      assert(profiles.flatMap(_.toSingle).toSet === singles.toSet)
    }
  }

}
