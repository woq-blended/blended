package blended.updater.config

import scala.util.Success

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class FeatureRefCompanionSpec extends LoggingFreeSpec with ScalaCheckPropertyChecks {

  "conversion to and from config" in {

    import TestData._

    forAll { feature: FeatureRef =>
      assert(FeatureRefCompanion.fromConfig(FeatureRefCompanion.toConfig(feature)) === Success(feature))
    }

  }

}
