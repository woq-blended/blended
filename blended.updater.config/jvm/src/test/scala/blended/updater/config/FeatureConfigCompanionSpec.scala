package blended.updater.config

import scala.util.Success

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class FeatureConfigCompanionSpec extends LoggingFreeSpec with ScalaCheckPropertyChecks {

  "conversion to and from config" in {

    import TestData._

    forAll { feature: FeatureConfig =>
      assert(FeatureConfigCompanion.read(FeatureConfigCompanion.toConfig(feature)) === Success(feature))
    }


  }

}
